package io.crest.extensions.datasource.dto;

import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class FieldGroupDTO {
    private String name;

    private List<String> text;

    private String min;

    private String minTerm;

    private String max;

    private String maxTerm;

    private String startTime;

    private String endTime;
}
