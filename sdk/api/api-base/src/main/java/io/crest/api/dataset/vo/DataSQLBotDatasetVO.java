package io.crest.api.dataset.vo;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class DataSQLBotDatasetVO implements Serializable {

    private String tableId;

    private String tableName;

    private String dsId;

    private String dsName;

}
