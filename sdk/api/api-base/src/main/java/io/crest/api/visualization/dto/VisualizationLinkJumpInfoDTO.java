package io.crest.api.visualization.dto;

import io.crest.api.visualization.vo.VisualizationLinkJumpInfoVO;
import io.crest.api.visualization.vo.VisualizationLinkJumpTargetViewInfoVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条组件跳转规则及目标视图配置。
 */
@Data
public class VisualizationLinkJumpInfoDTO extends VisualizationLinkJumpInfoVO {
    private String sourceFieldName;

    private String sourceJumpInfo;

    private Integer sourceFieldType;

    // 目标仪表板存在公共链接时记录对应链接标识。
    private String publicJumpId;

    // 目标类型
    private String targetDvType;

    private List<VisualizationLinkJumpTargetViewInfoVO> targetViewInfoList=new ArrayList<>();// linkType 为 inner 时使用。

}
