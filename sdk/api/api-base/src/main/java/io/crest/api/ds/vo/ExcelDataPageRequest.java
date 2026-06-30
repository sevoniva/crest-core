package io.crest.api.ds.vo;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class ExcelDataPageRequest {
    private Long datasourceId;
    private String tableName;
    private Integer page = 1;
    private Integer pageSize = 100;
}
