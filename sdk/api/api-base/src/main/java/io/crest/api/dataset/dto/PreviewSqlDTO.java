package io.crest.api.dataset.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class PreviewSqlDTO {
    private String tableId;
    private String sql;
    private Long datasourceId;
    private String sqlVariableDetails;
    private Boolean isCross;
}
