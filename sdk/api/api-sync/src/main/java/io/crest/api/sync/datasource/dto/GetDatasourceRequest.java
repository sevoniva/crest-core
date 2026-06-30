package io.crest.api.sync.datasource.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class GetDatasourceRequest extends SyncDatasourceDTO {

    /**
     * 查询sql
     */
    private String query;
    /**
     * 表名
     */
    private String table;
    /**
     * 表格的抽取数据方式
     */
    private boolean tableExtract;

    private String targetDbId;

}
