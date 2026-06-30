package io.crest.api.chart.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
// 定义接口请求或返回数据的传输结构
public class Series {
    private String name;
    private String type;
    private List<Object> data;
    private Set<String> categories;
}
