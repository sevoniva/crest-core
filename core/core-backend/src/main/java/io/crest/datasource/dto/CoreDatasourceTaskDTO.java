package io.crest.datasource.dto;

import io.crest.datasource.dao.auto.entity.CoreDatasourceTask;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 定义接口请求或返回数据的传输结构
public class CoreDatasourceTaskDTO extends CoreDatasourceTask {
    private String datasourceName;
    private Long nextExecTime;
    private String taskStatus;
    private String msg;
    private String privileges;
}
