package io.crest.api.ds.vo;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class RemoteExcelRequest extends ExcelConfiguration {
    private Long datasourceId;
    private int editType;
}
