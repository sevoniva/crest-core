package io.crest.api.webhook.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class WebhookSwitchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -2194668885324981185L;

    private Long id;

    private boolean ssl;
}
