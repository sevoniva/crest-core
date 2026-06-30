package io.crest.job.schedule;

import io.crest.commons.utils.CronUtils;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("dataFillingTaskExecutor")
/**
 * 数据填报调度任务执行器，负责维护正式、临时和重试调度任务
 */
public class DataFillingTaskExecutor {

    protected static final String IS_TEMP_TASK = "isTempTask";
    protected static final String IS_RETRY_TASK = "isRetryTask";

    private static final String JOB_GROUP = "DATA_FILLING_TASK";
    private static final String RETRY_JOB_GROUP = "RETRY_DATA_FILLING_TASK";
    private static final String TEMP_JOB_GROUP = "TEMP_DATA_FILLING_TASK";

    @Resource
    private ScheduleManager scheduleManager;
    @Resource
    private ScheduledTaskQueueService queueService;
    @Autowired(required = false)
    private ObjectProvider<ScheduledDataFillingTaskRunner> dataFillingTaskRunnerProvider;
    /**
     * Quartz 只负责触发和投递，生产队列启用后由 Worker 统一消费。
     */
    public boolean execute(Map<String, Object> taskData) {
        return execute(taskData, null, null);
    }

    /**
     * 队列路径补充稳定触发键，用于数据库 CAS 识别同一次 Quartz 触发。
     */
    public boolean execute(Map<String, Object> taskData, String queueJobKey, Long scheduledFireTime) {
        if (queueService != null && queueService.enabled()) {
            queueService.enqueueDataFillingTask(queueTaskData(taskData, queueJobKey, scheduledFireTime));
            return true;
        }
        return runDataFillingTask(taskData, true);
    }
    /**
     * Worker 队列路径必须真实执行；未接入执行器时交由队列侧进入死信。
     */
    public boolean executeQueued(Map<String, Object> taskData) {
        return runDataFillingTask(taskData, false);
    }

    private boolean runDataFillingTask(Map<String, Object> taskData, boolean keepScheduleWhenMissingRunner) {
        ScheduledDataFillingTaskRunner runner = dataFillingTaskRunner();
        if (runner != null) {
            return runner.executeDataFillingTask(taskData);
        }
        LogUtil.warn("Data filling task runner is not configured");
        return keepScheduleWhenMissingRunner;
    }

    private ScheduledDataFillingTaskRunner dataFillingTaskRunner() {
        return dataFillingTaskRunnerProvider == null ? null : dataFillingTaskRunnerProvider.getIfAvailable();
    }

    private Map<String, Object> queueTaskData(Map<String, Object> taskData, String queueJobKey, Long scheduledFireTime) {
        if (queueJobKey == null && scheduledFireTime == null) {
            return taskData;
        }
        Map<String, Object> copy = taskData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(taskData);
        if (queueJobKey != null) {
            copy.put(ScheduledTaskQueueService.QUEUE_JOB_KEY, queueJobKey);
        }
        if (scheduledFireTime != null) {
            copy.put(ScheduledTaskQueueService.QUEUE_SCHEDULED_FIRE_TIME, scheduledFireTime);
        }
        return copy;
    }

    /**
     * 初始化数据填报调度执行器
     */
    public void init() {
    }

    /**
     * 新增或更新数据填报定时任务
     */
    public void addOrUpdateTask(Long taskId, String cron, Long startTime, Long endTime) {
        if (CronUtils.taskExpire(endTime)) {
            return;
        }
        String key = taskId.toString();
        JobKey jobKey = new JobKey(key, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(key, JOB_GROUP);
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("taskId", taskId);
        jobDataMap.put(IS_TEMP_TASK, false);
        Date end = null;
        if (ObjectUtils.isNotEmpty(endTime)) end = new Date(endTime);
        scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, DataFillingScheduleJob.class, cron, startTime == null ? null : new Date(startTime), end, jobDataMap);
    }

    /**
     * 新增数据填报重试任务
     */
    public void addRetryTask(Long taskId, Integer retryLimit, Integer retryInterval) {
        long saltTime = 3000L;
        long interval = retryInterval == null ? 5L : retryInterval;
        long intervalMill = interval * 60000L;
        long now = System.currentTimeMillis();
        String cron = "0 */" + interval + " * * * ?";
        long endTime = (retryLimit + 1) * intervalMill + now - saltTime;
        String key = taskId.toString();
        if (CronUtils.taskExpire(endTime)) {
            return;
        }
        JobKey jobKey = new JobKey(key, RETRY_JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(key, RETRY_JOB_GROUP);
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("taskId", taskId);
        jobDataMap.put(IS_RETRY_TASK, true);
        Date end = null;
        if (ObjectUtils.isNotEmpty(endTime)) end = new Date(endTime);
        scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, DataFillingScheduleJob.class, cron, new Date(now), end, jobDataMap);
    }

    /**
     * 立即触发指定数据填报任务
     */
    public boolean fireNow(Long taskId) throws Exception {
        String key = taskId.toString();
        JobKey jobKey = new JobKey(key, JOB_GROUP);
        if (scheduleManager.exist(jobKey)) {
            scheduleManager.fireNow(jobKey);
            return true;
        }
        return false;
    }

    /**
     * 新增一次性临时数据填报任务
     */
    public void addTempTask(Long taskId, Long startTime) {
        String key = taskId.toString();
        JobKey jobKey = new JobKey(key, TEMP_JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(key, TEMP_JOB_GROUP);
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(IS_TEMP_TASK, true);
        String cron = CronUtils.tempCron();
        jobDataMap.put("taskId", taskId);
        scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, DataFillingScheduleJob.class, cron, new Date(startTime), null, jobDataMap);
    }

    /**
     * 删除正式或临时数据填报任务
     */
    public void removeTask(Long taskId, boolean isTemp) {
        String key = taskId.toString();
        JobKey jobKey = new JobKey(key, isTemp ? TEMP_JOB_GROUP : JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(key, isTemp ? TEMP_JOB_GROUP : JOB_GROUP);
        scheduleManager.removeJob(jobKey, triggerKey);
    }

    /**
     * 删除数据填报重试任务
     */
    public void removeRetryTask(Long taskId) {
        String key = taskId.toString();
        JobKey jobKey = new JobKey(key, RETRY_JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(key, RETRY_JOB_GROUP);
        scheduleManager.removeJob(jobKey, triggerKey);
    }

    /**
     * 清理全部数据填报重试任务
     */
    public void clearRetryTask() throws Exception {
        scheduleManager.clearByGroup(RETRY_JOB_GROUP);
    }
}
