package io.crest.api.dataset.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetSyncLogDTO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetGroupId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private String updateType;

    private String tableName;

    private Long startTime;

    private Long endTime;

    private String taskStatus;

    private Long rowCount;

    private String info;

    private Long createTime;

    private String triggerType;
}
