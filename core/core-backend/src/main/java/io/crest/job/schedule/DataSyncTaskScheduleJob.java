package io.crest.job.schedule;

import io.crest.lock.CrestRedisLockService;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSyncTaskScheduleJob implements Job {


    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        DataSyncTaskExecutor taskExecutor = CommonBeanFactory.getBean(DataSyncTaskExecutor.class);
        assert taskExecutor != null;
        CrestRedisLockService lockService = CommonBeanFactory.getBean(CrestRedisLockService.class);
        if (lockService == null) {
            executeTask(taskExecutor, jobDataMap);
            return;
        }
        Optional<CrestRedisLockService.LockHandle> lock = lockService.tryLock(quartzLockName(jobExecutionContext));
        if (lock.isEmpty()) {
            return;
        }
        try (CrestRedisLockService.LockHandle ignored = lock.get()) {
            executeTask(taskExecutor, jobDataMap);
        }
    }

    private void executeTask(DataSyncTaskExecutor taskExecutor, JobDataMap jobDataMap) {
        try {
            taskExecutor.execute(jobDataMap);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e.getCause());
        }
    }

    private String quartzLockName(JobExecutionContext context) {
        return "quartz:" + context.getTrigger().getJobKey().getGroup() + ":" + context.getTrigger().getJobKey().getName();
    }
}
