package io.crest.extensions.view.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class AxisChartDataAntVDTO {
    private BigDecimal value;
    private List<ChartDimensionDTO> dimensionList;
    private List<ChartQuotaDTO> quotaList;
    private String field;
    private String name;
    private String category;
    private BigDecimal popSize;
    private BigDecimal x;
    @JsonProperty("xLabel")
    private String xLabel;
    private BigDecimal y;
    private BigDecimal lightness;
    private String group;
    private List<DynamicValueDTO> dynamicLabelValue;
    private List<DynamicValueDTO> dynamicTooltipValue;
}
