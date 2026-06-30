package io.crest.extensions.view.dto;

import lombok.Data;

/**
 * 阈值动态字段配置。
 */
@Data
public class ThresholdDynamicFieldDTO {
    /**
     * 字段id
     */
    private String fieldId;
    /**
     * 字段
     */
    private ChartViewFieldDTO field;
    /**
     * 条件
     */
    private String summary;
}
