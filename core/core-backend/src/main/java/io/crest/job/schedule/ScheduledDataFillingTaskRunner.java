package io.crest.job.schedule;

import java.util.Map;

// 数据填报调度真实执行入口，由业务模块提供具体实现。
public interface ScheduledDataFillingTaskRunner {

    boolean executeDataFillingTask(Map<String, Object> taskData);
}
