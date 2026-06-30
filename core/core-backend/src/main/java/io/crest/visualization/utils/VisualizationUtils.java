package io.crest.visualization.utils;

import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.utils.JsonUtil;

import java.util.HashMap;
import java.util.Map;

// 提供当前模块复用的工具能力
public class VisualizationUtils {

    // 转换输入数据并返回目标格式
    public static Map<Long, String> viewTransToStr(Map<Long, ChartViewDTO> source) {
        Map<Long, String> result = new HashMap<>();
        source.forEach((key, value) -> {
            result.put(key, (String) JsonUtil.toJSONString(value));
        });
        return result;
    }

    // 转换输入数据并返回目标格式
    public static Map<Long, ChartViewDTO> viewTransToObj(Map<Long, String> source) {
        Map<Long, ChartViewDTO> result = new HashMap<>();
        source.forEach((key, value) -> {
            result.put(key, JsonUtil.parseObject(value, ChartViewDTO.class));
        });
        return result;
    }
}
