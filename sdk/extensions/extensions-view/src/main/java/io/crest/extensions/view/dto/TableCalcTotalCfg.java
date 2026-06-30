package io.crest.extensions.view.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class TableCalcTotalCfg {
    private String engineFieldName;
    private String aggregation;
    private String originName;
    private int extField;
    private long chartId;
    private long datasetGroupId;
}
