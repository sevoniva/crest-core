package io.crest.api.visualization.response;

import io.crest.api.visualization.dto.VisualizationLinkJumpInfoDTO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 组件跳转基础配置响应。
 */
@Data
public class VisualizationLinkJumpBaseResponse {

    // 跳转规则明细映射。
    private Map<String, VisualizationLinkJumpInfoDTO> baseJumpInfoMap;

    // 按目标可视化类型聚合的跳转目标映射。
    private Map<String, List<String>> baseJumpInfoVisualizationMap;


    public VisualizationLinkJumpBaseResponse(Map<String, VisualizationLinkJumpInfoDTO> baseJumpInfoMap, Map<String, List<String>> baseJumpInfoVisualizationMap) {
        this.baseJumpInfoMap = baseJumpInfoMap;
        this.baseJumpInfoVisualizationMap = baseJumpInfoVisualizationMap;
    }

    public VisualizationLinkJumpBaseResponse() {

    }
}
