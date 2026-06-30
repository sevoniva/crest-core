package io.crest.api.visualization.dto;

import io.crest.api.visualization.vo.VisualizationLinkJumpVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件跳转配置及其目标映射信息。
 */
@Data
public class VisualizationLinkJumpDTO extends VisualizationLinkJumpVO {
    // 可作为跳转来源的图表信息，按 sourceViewId 组织。
    private String sourceInfo;

    private List<String> targetInfoList;

    private List<VisualizationLinkJumpInfoDTO> linkJumpInfoArray = new ArrayList<>();

    private Map<String,VisualizationLinkJumpInfoDTO> mapJumpInfoArray = new HashMap<>();
}
