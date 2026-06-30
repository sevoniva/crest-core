package io.crest.api.visualization.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 外部参数目标视图字段映射。
 */
@Data
public class VisualizationOuterParamsTargetViewInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String targetId;

    /**
     * core_visualization_parameter_item 表的 ID
     */
    private String paramsInfoId;

    /**
     * 联动视图ID
     */
    private String targetViewId;

    /**
     * 联动数据集id/联动过滤组件id
     */
    private String targetDsId;

    /**
     * 联动字段ID
     */
    private String targetFieldId;

    /**
     * 复制来源
     */
    private String copyFrom;

    /**
     * 复制来源ID
     */
    private String copyId;


    private String matchMode;
}
