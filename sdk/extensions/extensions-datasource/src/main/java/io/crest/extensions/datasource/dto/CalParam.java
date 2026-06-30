package io.crest.extensions.datasource.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class CalParam implements Serializable {
    private String id;
    private String name;
    private String value;
}
