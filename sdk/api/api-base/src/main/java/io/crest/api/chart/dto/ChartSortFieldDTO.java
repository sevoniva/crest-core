package io.crest.api.chart.dto;

import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartSortFieldDTO extends DatasetTableFieldDTO {

    private String orderDirection;
}
