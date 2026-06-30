package io.crest.api.free.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class FreeQueryRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 7951259501228286914L;

    private int rtId;
}
