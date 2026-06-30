package io.crest.job.schedule;

import java.util.Map;

// 报表调度真实执行入口，由业务模块提供具体实现。
public interface ScheduledReportTaskRunner {

    boolean executeReportTask(Map<String, Object> taskData);
}
