package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartCalcDataResult {
    private Map<String, Object> data;
    private List<String[]> originData;
    private List<String[]> assistData;
    private List<ChartSeniorAssistDTO> dynamicAssistFields;
    private List<String[]> assistDataOriginList;
    private List<ChartSeniorAssistDTO> dynamicAssistFieldsOriginList;
    private Map<String, Object> context;
    // 插件图表计算时保留最终查询 SQL，供后续执行链路复用。
    private String querySql;
}
