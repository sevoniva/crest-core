package io.crest.job.schedule;


import io.crest.exception.CrestException;
import io.crest.i18n.Translator;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Quartz 任务调度管理器，封装任务新增、修改、触发和清理操作
 */
@Component
@SuppressWarnings("unchecked")
public class ScheduleManager {

    /**
     * Quartz 调度器实例
     */
    @Resource
    private Scheduler scheduler;

    /**
     * 添加 simpleJob
     *
     * @param jobKey
     * @param triggerKey
     * @param cls
     * @param repeatIntervalTime
     * @param jobDataMap
     * @throws SchedulerException
     */
    public void addSimpleJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> cls, int repeatIntervalTime,
                             JobDataMap jobDataMap) throws SchedulerException {

        JobBuilder jobBuilder = JobBuilder.newJob(cls).withIdentity(jobKey);

        if (jobDataMap != null) {
            jobBuilder.usingJobData(jobDataMap);
        }

        JobDetail jd = jobBuilder.build();

        SimpleTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(repeatIntervalTime).repeatForever())
                .startNow().build();

        scheduler.scheduleJob(jd, trigger);
    }

    /**
     * 添加不带任务数据的 simpleJob
     */
    public void addSimpleJob(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> cls, int repeatIntervalTime)
            throws SchedulerException {
        addSimpleJob(jobKey, triggerKey, cls, repeatIntervalTime);
    }

    /**
     * 添加 cronJob
     *
     * @param jobKey
     * @param triggerKey
     * @param jobClass
     * @param cron
     * @param jobDataMap
     */
    public void addCronJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, String cron, Date startTime,
                           Date endTime, JobDataMap jobDataMap) {
        try {

            LogUtil.info("addCronJob: " + triggerKey.getName() + "," + triggerKey.getGroup());

            JobBuilder jobBuilder = JobBuilder.newJob(jobClass).withIdentity(jobKey);
            if (jobDataMap != null) {
                jobBuilder.usingJobData(jobDataMap);
            }
            JobDetail jobDetail = jobBuilder.build();

            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();

            triggerBuilder.withIdentity(triggerKey);

            Date nTimeByCron = getNTimeByCron(cron, startTime);
            triggerBuilder.startAt(nTimeByCron);

            if (endTime != null) {
                if (endTime.before(nTimeByCron)) {
                    triggerBuilder.endAt(nTimeByCron);
                } else {
                    triggerBuilder.endAt(endTime);
                }
            } else {
                triggerBuilder.endAt(null);
            }

            triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cron));

            CronTrigger trigger = (CronTrigger) triggerBuilder.build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 添加不带任务数据的 cronJob
     */
    public void addCronJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, String cron, Date startTime,
                           Date endTime) {
        addCronJob(jobKey, triggerKey, jobClass, cron, startTime, endTime, null);
    }

    /**
     * 添加指定时间执行一次的任务
     */
    public void addSingleJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, Date date, JobDataMap jobDataMap) {
        try {
            LogUtil.info("addSingleJob: " + triggerKey.getName() + "," + triggerKey.getGroup());

            JobBuilder jobBuilder = JobBuilder.newJob(jobClass).withIdentity(jobKey);
            if (jobDataMap != null) {
                jobBuilder.usingJobData(jobDataMap);
            }
            JobDetail jobDetail = jobBuilder.build();

            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();

            triggerBuilder.withIdentity(triggerKey);

            triggerBuilder.startAt(date).startNow();
            Trigger trigger = triggerBuilder.build();

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 添加不带任务数据的单次任务
     */
    public void addSingleJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, Date date) {
        addSingleJob(jobKey, triggerKey, jobClass, date, null);
    }

    /**
     * 修改 cronTrigger
     *
     * @param triggerKey
     * @param cron
     * @throws SchedulerException
     */
    public void modifyCronJobTime(TriggerKey triggerKey, String cron, Date startTime, Date endTime)
            throws SchedulerException {

        LogUtil.info("modifyCronJobTime: " + triggerKey.getName() + "," + triggerKey.getGroup());

        try {
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

            if (trigger == null) {
                return;
            }

            /** 方式一 ：调用 rescheduleJob 开始 */
            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();// 触发器

            triggerBuilder.withIdentity(triggerKey);// 触发器名,触发器组

            Date nTimeByCron = getNTimeByCron(cron, startTime);
            triggerBuilder.startAt(nTimeByCron);

            if (endTime != null) {
                if (endTime.before(nTimeByCron)) {
                    triggerBuilder.endAt(nTimeByCron);
                } else {
                    triggerBuilder.endAt(endTime);
                }
            } else {
                triggerBuilder.endAt(null);
            }

            triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cron));// 触发器时间设定

            trigger = (CronTrigger) triggerBuilder.build();// 创建Trigger对象

            scheduler.rescheduleJob(triggerKey, trigger);// 修改一个任务的触发时间
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 修改simpleTrigger触发器的触发时间
     *
     * @param triggerKey
     * @param repeatIntervalTime
     * @throws SchedulerException
     */
    public void modifySimpleJobTime(TriggerKey triggerKey, int repeatIntervalTime) throws SchedulerException {

        try {

            LogUtil.info("modifySimpleJobTime: " + triggerKey.getName() + "," + triggerKey.getGroup());

            SimpleTrigger trigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);

            if (trigger == null) {
                return;
            }

            long oldTime = trigger.getRepeatInterval();

            if (oldTime != repeatIntervalTime) {

                /** 方式一 ：调用 rescheduleJob 开始 */
                TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();// 触发器builder

                triggerBuilder.withIdentity(triggerKey);// 触发器名,触发器组

                triggerBuilder.withSchedule(SimpleScheduleBuilder.repeatHourlyForever(repeatIntervalTime));// 更新触发器的重复间隔时间

                triggerBuilder.startNow();// 立即执行

                trigger = (SimpleTrigger) triggerBuilder.build();// 创建Trigger对象

                scheduler.rescheduleJob(triggerKey, trigger);// 修改一个任务的触发时间
            }

        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 修改单次任务触发时间
     */
    public void modifySingleJobTime(TriggerKey triggerKey, Date date) throws SchedulerException {

        try {

            LogUtil.info("modifySingleJobTime: " + triggerKey.getName() + "," + triggerKey.getGroup());

            Trigger trigger = scheduler.getTrigger(triggerKey);

            if (trigger == null) {
                return;
            }

            Date oldTime = trigger.getStartTime();

            if (oldTime.getTime() != date.getTime()) {

                TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();// 触发器builder

                triggerBuilder.withIdentity(triggerKey);// 触发器名,触发器组

                triggerBuilder.startAt(date);

                trigger = triggerBuilder.build();// 创建Trigger对象

                scheduler.rescheduleJob(triggerKey, trigger);// 修改一个任务的触发时间
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * @param jobKey
     * @param triggerKey
     * @Title:
     * @Description: 根据job和trigger删除任务
     */
    public void removeJob(JobKey jobKey, TriggerKey triggerKey) {

        try {

            LogUtil.info("RemoveJob: " + jobKey.getName() + "," + jobKey.getGroup());

            scheduler.pauseTrigger(triggerKey);

            scheduler.unscheduleJob(triggerKey);

            scheduler.deleteJob(jobKey);

        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 启动指定调度器
     */
    public static void startJobs(Scheduler sched) {
        try {
            sched.start();
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 关闭指定调度器
     */
    public void shutdownJobs(Scheduler sched) {
        try {
            if (!sched.isShutdown()) {
                sched.shutdown();
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 新增或者修改 simpleJob
     *
     * @param jobKey
     * @param triggerKey
     * @param clz
     * @param intervalTime
     * @param jobDataMap
     * @throws SchedulerException
     */
    public void addOrUpdateSimpleJob(JobKey jobKey, TriggerKey triggerKey, Class clz,
                                     int intervalTime, JobDataMap jobDataMap) throws SchedulerException {

        if (scheduler.checkExists(triggerKey)) {
            modifySimpleJobTime(triggerKey, intervalTime);
        } else {
            addSimpleJob(jobKey, triggerKey, clz, intervalTime, jobDataMap);
        }

    }

    /**
     * 新增或更新单次执行任务
     */
    public void addOrUpdateSingleJob(JobKey jobKey, TriggerKey triggerKey, Class clz,
                                     Date date, JobDataMap jobDataMap) throws CrestException {
        try {
            if (scheduler.checkExists(triggerKey)) {
                modifySingleJobTime(triggerKey, date);
            } else {
                addSingleJob(jobKey, triggerKey, clz, date, jobDataMap);
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 新增或更新不带任务数据的单次执行任务
     */
    public void addOrUpdateSingleJob(JobKey jobKey, TriggerKey triggerKey, Class clz,
                                     Date date) throws SchedulerException {
        addOrUpdateSingleJob(jobKey, triggerKey, clz, date, null);
    }

    /**
     * 新增或更新不带任务数据的 simpleJob
     */
    public void addOrUpdateSimpleJob(JobKey jobKey, TriggerKey triggerKey, Class clz, int intervalTime)
            throws SchedulerException {
        addOrUpdateSimpleJob(jobKey, triggerKey, clz, intervalTime, null);
    }

    /**
     * 添加或修改 cronJob
     *
     * @param jobKey
     * @param triggerKey
     * @param jobClass
     * @param cron
     * @param jobDataMap
     * @throws SchedulerException
     */
    public void addOrUpdateCronJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, String cron, Date startTime,
                                   Date endTime, JobDataMap jobDataMap) throws CrestException {

        LogUtil.info("AddOrUpdateCronJob: " + jobKey.getName() + "," + triggerKey.getGroup());
        try {
            if (scheduler.checkExists(triggerKey)) {
                modifyCronJobTime(triggerKey, cron, startTime, endTime);
            } else {
                addCronJob(jobKey, triggerKey, jobClass, cron, startTime, endTime, jobDataMap);
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 添加或更新不带任务数据的 cronJob
     */
    public void addOrUpdateCronJob(JobKey jobKey, TriggerKey triggerKey, Class jobClass, String cron, Date startTime,
                                   Date endTime) throws SchedulerException {
        addOrUpdateCronJob(jobKey, triggerKey, jobClass, cron, startTime, endTime, null);
    }

    /**
     * 构建数据集更新任务的默认 JobDataMap
     */
    public JobDataMap getDefaultJobDataMap(String resourceId, String expression, String taskId, String updateType) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("datasetTableId", resourceId);
        jobDataMap.put("taskId", taskId);
        jobDataMap.put("expression", expression);
        jobDataMap.put("updateType", updateType);
        return jobDataMap;
    }

    /**
     * 查询当前正在执行的任务信息
     */
    public Object getCurrentlyExecutingJobs() {
        Map<String, String> returnMap = new HashMap<>();
        try {
            List<JobExecutionContext> currentJobs = scheduler.getCurrentlyExecutingJobs();
            for (JobExecutionContext jobCtx : currentJobs) {
                String jobName = jobCtx.getJobDetail().getKey().getName();
                String groupName = jobCtx.getJobDetail().getJobClass().getName();

                returnMap.put("jobName", jobName);
                returnMap.put("groupName", groupName);
            }
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }

        return returnMap;
    }

    /**
     * 根据 cron 表达式创建 CronTrigger
     */
    public static CronTrigger getCronTrigger(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            String msg = Translator.get("I18N_CRON_ERROR");
            CrestException.throwException(msg + " : " + cron);
        }
        return TriggerBuilder.newTrigger().withIdentity("Calculate Date")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();

    }

    /**
     * 获取指定开始时间之后的下一次 cron 触发时间
     */
    public static Date getNTimeByCron(String cron, Date start) {
        CronTrigger trigger = getCronTrigger(cron);
        if (start == null) {
            start = trigger.getStartTime();
        }
        return trigger.getFireTimeAfter(start);
    }

    /**
     * 按任务名称和分组立即触发任务
     */
    public void fireNow(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        scheduler.triggerJob(jobKey);
    }

    /**
     * 按 JobKey 立即触发任务
     */
    public void fireNow(JobKey jobKey) throws SchedulerException {
        scheduler.triggerJob(jobKey);
    }

    /**
     * 判断指定任务是否存在
     */
    public boolean exist(JobKey jobKey) {
        try {
            return scheduler.checkExists(jobKey);
        } catch (SchedulerException e) {
            LogUtil.error(e.getMessage(), new Throwable(e));
            return false;
        }
    }

    /**
     * 清理指定分组下的任务和触发器
     */
    public void clearByGroup(String groupName) throws Exception {
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.groupEquals(groupName));
        Set<TriggerKey> triggerKeys = scheduler.getTriggerKeys(GroupMatcher.groupEquals(groupName));
        scheduler.pauseTriggers(GroupMatcher.groupEquals(groupName));
        scheduler.unscheduleJobs(new ArrayList<>(triggerKeys));
        scheduler.deleteJobs(new ArrayList<>(jobKeys));
    }

    /**
     * 添加或修改 simpleJob,自定义开始时间和结束时间
     *
     */
    public void addOrUpdateSimpleJobForCustomTime(JobKey jobKey, TriggerKey triggerKey, Class clz, Date startTime, Date endTime,
                                                  String period, JobDataMap jobDataMap) throws SchedulerException {

        if (scheduler.checkExists(triggerKey)) {
            modifySimpleJobTimeForCustomTime(triggerKey, period, startTime, endTime);
        } else {
            addSimpleJobForCustomTime(jobKey, triggerKey, clz, period, startTime, endTime, jobDataMap);
        }

    }

    /**
     * 添加带自定义开始和结束时间的 simpleJob
     */
    public void addSimpleJobForCustomTime(JobKey jobKey, TriggerKey triggerKey, Class<? extends Job> cls,
                                          String period, Date startTime, Date endTime, JobDataMap jobDataMap)
            throws SchedulerException {
        JobDataMap dateMap = jobDataMap != null ? jobDataMap : new JobDataMap();
        dateMap.put("period", period);
        JobDetail jobDetail = JobBuilder.newJob(cls)
                .withIdentity(jobKey)
                .usingJobData(dateMap)
                .build();
        TriggerBuilder<SimpleTrigger> triggerBuilder = simpleJobTriggerBuilder(triggerKey, period, startTime, endTime);
        triggerBuilder.usingJobData(dateMap);
        scheduler.scheduleJob(jobDetail, triggerBuilder.build());
    }

    /**
     * 修改 simpleTrigger 的间隔、开始时间和结束时间
     */
    public void modifySimpleJobTimeForCustomTime(TriggerKey triggerKey, String period, Date startTime, Date endTime) {
        try {
            LogUtil.info("modifySimpleJobTimeForCustomTime: " + triggerKey.getName() + "," + triggerKey.getGroup());
            SimpleTrigger trigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                return;
            }
            Date oldStartTime = trigger.getStartTime();
            Date oldEndTime = trigger.getEndTime();
            String oldPeriod = trigger.getJobDataMap().getString("period");
            boolean startTimeChanged = !Objects.equals(oldStartTime, startTime);
            boolean endTimeChanged = !Objects.equals(oldEndTime, endTime);
            boolean periodChanged = !Objects.equals(oldPeriod, period);
            if (startTimeChanged || endTimeChanged || periodChanged) {
                TriggerBuilder<SimpleTrigger> triggerBuilder = simpleJobTriggerBuilder(triggerKey, period, startTime, endTime);
                triggerBuilder.usingJobData(trigger.getJobDataMap());
                scheduler.rescheduleJob(triggerKey, triggerBuilder.build());
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 构建 simpleTrigger 触发器
     */
    private TriggerBuilder<SimpleTrigger> simpleJobTriggerBuilder(TriggerKey triggerKey, String period, Date startTime, Date endTime) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule();
        if (period != null && period.length() > 1) {
            String number = period.substring(0, period.length() - 1);
            char unit = period.charAt(period.length() - 1);
            switch (unit) {
                case 's':
                    scheduleBuilder.withIntervalInSeconds(Integer.parseInt(number));
                    break;
                case 'm':
                    scheduleBuilder.withIntervalInMinutes(Integer.parseInt(number));
                    break;
                case 'h':
                    scheduleBuilder.withIntervalInHours(Integer.parseInt(number));
                    break;
                case 'd':
                    scheduleBuilder.withIntervalInHours(Integer.parseInt(number) * 24);
                    break;
                default:
                    scheduleBuilder.withIntervalInMinutes(1);
            }
            scheduleBuilder.repeatForever();
        } else {
            scheduleBuilder.withIntervalInMinutes(1);
        }
        TriggerBuilder<SimpleTrigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(scheduleBuilder);
        if (startTime != null) {
            triggerBuilder.startAt(startTime);
        } else {
            triggerBuilder.startNow();
        }
        triggerBuilder.endAt(endTime);
        return triggerBuilder;
    }

    /**
     * 获取间隔任务的下一次执行时间
     */
    public Long getNextSimpleTriggerTime(TriggerKey triggerKey, Date currentTime) {
        try {
            SimpleTrigger trigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                LogUtil.warn("getNextSimpleTriggerTime: " + triggerKey.getName() + "," + triggerKey.getGroup());
                return null;
            }
            LogUtil.debug("SimpleTriggerNextTime: " + triggerKey.getName() + "," + triggerKey.getGroup() + "," + trigger.getFireTimeAfter(currentTime));
            return trigger.getFireTimeAfter(currentTime) != null ? trigger.getFireTimeAfter(currentTime).getTime() : null;
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
        return null;
    }

    /**
     * 暂停指定触发器
     */
    public void pauseTrigger(TriggerKey triggerKey) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger != null) {
                scheduler.pauseTrigger(triggerKey);
            } else {
                LogUtil.warn("pauseTrigger: " + triggerKey.getName() + "," + triggerKey.getGroup());
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }

    /**
     * 恢复指定触发器
     */
    public void resumeTrigger(TriggerKey triggerKey) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger != null) {
                scheduler.resumeTrigger(triggerKey);
            } else {
                LogUtil.warn("resumeTrigger: " + triggerKey.getName() + "," + triggerKey.getGroup());
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
    }
}
