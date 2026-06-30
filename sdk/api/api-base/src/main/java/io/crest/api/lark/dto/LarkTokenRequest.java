package io.crest.api.lark.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class LarkTokenRequest implements Serializable {

    private String code;

    private String state;
}
