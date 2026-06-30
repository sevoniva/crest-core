package io.crest.api.sync.task.vo;

import lombok.Data;

/**
 * 数据同步任务日志。
 */
@Data
public class TaskLogVO {

    private String id;
    private String jobId;
    private String jobName;
    private String jobDesc;
    private Long executorStartTime;
    private Long executorEndTime;
    private String status;
    private String executorMsg;
    private String executorAddress;

    private String clearType;

}
