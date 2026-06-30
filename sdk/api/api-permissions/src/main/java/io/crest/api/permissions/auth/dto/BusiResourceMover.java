package io.crest.api.permissions.auth.dto;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class BusiResourceMover implements Serializable {

    private Long id;

    private Long pid;
}
