package io.crest.api.chart.request;

import io.crest.extensions.view.dto.ChartViewDTO;
import lombok.Data;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartExcelRequest extends ChartExcelRequestInner {
    @Serial
    private static final long serialVersionUID = 3829386417457449431L;

    private String dvId;

    private String busiFlag;

    private String viewId;

    private String viewName;

    private ChartViewDTO viewInfo;

    private List<ChartExcelRequestInner> multiInfo;

    private boolean embeddedExport = false;

    private String downloadType;

    private Map<String, Object> data;

}
