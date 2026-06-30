package io.crest.api.visualization.dto;


import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.view.dto.ChartViewDTO;
import lombok.Data;

import java.util.List;

/**
 * 可视化视图绑定的数据集表信息。
 */
@Data
public class VisualizationViewTableDTO extends ChartViewDTO {

    private String visualizationId;

    private String baseVisualizationData;

    private List<DatasetTableFieldDTO> tableFields;
}
