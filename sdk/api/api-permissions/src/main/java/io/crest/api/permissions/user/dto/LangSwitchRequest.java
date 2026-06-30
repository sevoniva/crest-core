package io.crest.api.permissions.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "语言切换器")
@Data
// 定义接口请求或返回数据的传输结构
public class LangSwitchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -6779697711311519431L;

    @Schema(description = "目标语言", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lang;
}
