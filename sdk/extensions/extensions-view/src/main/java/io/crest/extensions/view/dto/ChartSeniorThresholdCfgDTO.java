package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;


@Data
// 定义接口请求或返回数据的传输结构
public class ChartSeniorThresholdCfgDTO {
    /**
     * 是否启用
     */
    private boolean enable;

    /**
     * 表格阈值
     */
    private List<TableThresholdDTO> tableThreshold;
}
