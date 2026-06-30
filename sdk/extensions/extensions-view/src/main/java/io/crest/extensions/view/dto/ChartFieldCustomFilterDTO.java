package io.crest.extensions.view.dto;

import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartFieldCustomFilterDTO extends ChartViewFieldBaseDTO {
    private List<ChartCustomFilterItemDTO> filter;
    private DatasetTableFieldDTO field;
    private List<String> enumCheckField;
}
