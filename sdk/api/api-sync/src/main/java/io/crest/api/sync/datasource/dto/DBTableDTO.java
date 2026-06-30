package io.crest.api.sync.datasource.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 定义接口请求或返回数据的传输结构
public class DBTableDTO {
    private String datasourceId;
    private String name;
    private String remark;
    private boolean enableCheck;
    private String datasetPath;
}
