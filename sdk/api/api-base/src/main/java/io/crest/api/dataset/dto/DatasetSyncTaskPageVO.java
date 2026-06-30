package io.crest.api.dataset.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetSyncTaskPageVO implements Serializable {

    private Integer page;

    private Integer pageSize;

    private Long total;

    private List<DatasetSyncTaskDTO> records;
}
