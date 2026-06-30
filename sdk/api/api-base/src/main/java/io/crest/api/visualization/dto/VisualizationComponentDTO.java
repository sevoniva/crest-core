package io.crest.api.visualization.dto;

import io.crest.api.visualization.vo.VisualizationOutParamsJumpVO;
import io.crest.api.visualization.vo.VisualizationViewTableVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可视化组件导出和复制时使用的组件明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisualizationComponentDTO {

    private String bashComponentData;

    List<VisualizationViewTableVO> visualizationViewTables;

    List<VisualizationOutParamsJumpVO> outParamsJumpInfo;

}
