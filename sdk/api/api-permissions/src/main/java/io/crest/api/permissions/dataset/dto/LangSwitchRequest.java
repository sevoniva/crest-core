package io.crest.api.permissions.dataset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class LangSwitchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -6779697711311519431L;


    private String lang;
}
