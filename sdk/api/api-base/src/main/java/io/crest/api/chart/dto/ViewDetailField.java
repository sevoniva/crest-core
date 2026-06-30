package io.crest.api.chart.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class ViewDetailField implements Serializable {

    private String name;

    private String engineFieldName;

    private Integer fieldType;
}
