package io.crest.api.permissions.embedded.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class EmbeddedOrigin implements Serializable {

    private String token;

    private String origin;
}
