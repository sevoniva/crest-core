package io.crest.api.dataset.dto;


import io.crest.extensions.view.dto.ChartExtFilterDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class MultFieldValuesRequest {
    List<Long> fieldIds = new ArrayList<>();
    Long userId = null;
    private List<ChartExtFilterDTO> filter;// 级联查询条件，多个条件之间用and拼接到SQL

    private DatasetSortDTO sort;
    private Integer resultMode = 0;
    private Long visualizationId;

}
