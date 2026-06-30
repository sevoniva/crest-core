package io.crest.api.lark.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class LarkGroupItem implements Serializable {
    @Serial
    private static final long serialVersionUID = -3458959523154279946L;

    private String chat_id;
    private String name;
}
