package io.crest.api.dingtalk.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class SignatureRequest implements Serializable {

    private String currentUrl;
}
