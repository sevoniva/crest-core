package io.crest.api.system.request;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义模块接口契约和数据传输结构
public class SQLBotConfigCreator implements Serializable {

    private String domain;

    private String id;

    private Boolean enabled = false;

    private Boolean valid = false;
}
