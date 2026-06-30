package io.crest.extensions.view.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class TableTotalCfg {
    private boolean showGrandTotals;
    private boolean showSubTotals;
    private TableCalcTotal calcTotals;
    private TableCalcTotal calcSubTotals;
}
