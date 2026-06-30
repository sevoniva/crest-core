package io.crest.api.wecom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class WecomCreator implements Serializable {
    @Schema(description = "corpId", requiredMode = Schema.RequiredMode.REQUIRED)
    private String corpId;
    @Schema(description = "agentId", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentId;
    @Schema(description = "appSecret", requiredMode = Schema.RequiredMode.REQUIRED)
    private String appSecret;
    @Schema(description = "回调域名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String callBack;
    @Schema(description = "是否开启", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enable = false;
    @Schema(description = "是否可用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean valid = false;
}
