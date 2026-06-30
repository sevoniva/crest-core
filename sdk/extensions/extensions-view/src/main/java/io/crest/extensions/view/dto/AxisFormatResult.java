package io.crest.extensions.view.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class AxisFormatResult {
    private Map<ChartAxis, List<ChartViewFieldDTO>> axisMap;
    private Map<String, Object> context;
}
