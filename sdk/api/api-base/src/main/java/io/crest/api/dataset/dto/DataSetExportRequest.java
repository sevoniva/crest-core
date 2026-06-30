package io.crest.api.dataset.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class DataSetExportRequest extends DatasetNodeDTO {
    private String filename;
    private String expressionTree;
    private boolean embeddedExport;
}
