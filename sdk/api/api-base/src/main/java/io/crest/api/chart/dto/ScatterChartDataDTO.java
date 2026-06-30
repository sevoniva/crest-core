package io.crest.api.chart.dto;

import io.crest.extensions.view.dto.ChartDimensionDTO;
import io.crest.extensions.view.dto.ChartQuotaDTO;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ScatterChartDataDTO {
    private Object[] value;
    private List<ChartDimensionDTO> dimensionList;
    private List<ChartQuotaDTO> quotaList;
}
