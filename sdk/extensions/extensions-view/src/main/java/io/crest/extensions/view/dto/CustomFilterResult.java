package io.crest.extensions.view.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
// 定义接口请求或返回数据的传输结构
public class CustomFilterResult {
    private List<ChartExtFilterDTO> filterList;
    private Map<String, Object> context;
}
