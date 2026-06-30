package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;

/**
 * 明细表字段阈值配置。
 */
@Data
public class TableThresholdDTO {
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
    private List<ChartSeniorThresholdDTO> conditions;
}
