package io.crest.job.schedule;

import io.crest.lock.CrestRedisLockService;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import org.quartz.*;

public abstract class CrestScheduleJob implements Job {

    protected Long datasetTableId;
    protected String expression;
    protected Long taskId;
    protected String updateType;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobKey jobKey = context.getTrigger().getJobKey();
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        this.datasetTableId = jobDataMap.getLong("datasetTableId");
        this.expression = jobDataMap.getString("expression");
        this.taskId = jobDataMap.getLong("taskId");
        this.updateType = jobDataMap.getString("updateType");
        LogUtil.info(jobKey.getName() + " Running: " + datasetTableId);
        LogUtil.info("CronExpression: " + expression);
        CrestRedisLockService lockService = CommonBeanFactory.getBean(CrestRedisLockService.class);
        if (lockService == null) {
            businessExecute(context);
            return;
        }
        lockService.runWithLock(quartzLockName(context), () -> businessExecute(context));
    }

    abstract void businessExecute(JobExecutionContext context);

    protected String quartzLockName(JobExecutionContext context) {
        JobKey jobKey = context.getTrigger().getJobKey();
        return "quartz:" + jobKey.getGroup() + ":" + jobKey.getName();
    }
}
