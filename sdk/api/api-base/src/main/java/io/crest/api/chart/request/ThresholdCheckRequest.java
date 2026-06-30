package io.crest.api.chart.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class ThresholdCheckRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -8377694080272137660L;

    private Long chartId;

    private String thresholdRules;

    private String thresholdTemplate;

    private String resourceTable;

    private boolean showFieldValue;

    private Integer thresholdLimit = 5;
}
