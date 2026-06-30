package io.crest.job.schedule;

import io.crest.lock.CrestRedisLockService;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class ScheduledTaskJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Trigger trigger = jobExecutionContext.getTrigger();
        JobKey jobKey = trigger.getJobKey();
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        ScheduledTaskExecutor taskExecutor = CommonBeanFactory.getBean(ScheduledTaskExecutor.class);
        assert taskExecutor != null;
        CrestRedisLockService lockService = CommonBeanFactory.getBean(CrestRedisLockService.class);
        if (lockService == null) {
            executeTask(taskExecutor, jobDataMap, jobKey, trigger, jobExecutionContext);
            return;
        }
        Optional<CrestRedisLockService.LockHandle> lock = lockService.tryLock(quartzLockName(jobExecutionContext));
        if (lock.isEmpty()) {
            return;
        }
        try (CrestRedisLockService.LockHandle ignored = lock.get()) {
            executeTask(taskExecutor, jobDataMap, jobKey, trigger, jobExecutionContext);
        }
    }

    private void executeTask(ScheduledTaskExecutor taskExecutor, JobDataMap jobDataMap, JobKey jobKey, Trigger trigger,
                             JobExecutionContext context) {
        try {
            Long scheduledFireTime = context.getScheduledFireTime() == null ? null : context.getScheduledFireTime().getTime();
            boolean taskLoaded = taskExecutor.execute(jobDataMap, queueJobKey(jobKey), scheduledFireTime);
            if (!taskLoaded) {
                Objects.requireNonNull(CommonBeanFactory.getBean(ScheduleManager.class)).removeJob(jobKey, trigger.getKey());
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e.getCause());
        }
    }

    private String quartzLockName(JobExecutionContext context) {
        return "quartz:" + context.getTrigger().getJobKey().getGroup() + ":" + context.getTrigger().getJobKey().getName();
    }

    private String queueJobKey(JobKey jobKey) {
        return jobKey.getGroup() + ":" + jobKey.getName();
    }
}
