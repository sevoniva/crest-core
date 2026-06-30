package io.crest.extensions.view.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class AxisChartDataDTO {
    private BigDecimal value;
    private List<ChartDimensionDTO> dimensionList;
    private List<ChartQuotaDTO> quotaList;
}
