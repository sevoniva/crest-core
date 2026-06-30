package io.crest.api.dataset.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetSyncTaskRequest implements Serializable {

    private String keyword;

    private String taskStatus;

    private String syncRate;

    private String updateType;
}
