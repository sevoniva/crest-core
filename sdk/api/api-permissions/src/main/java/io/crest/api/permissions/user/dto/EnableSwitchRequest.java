package io.crest.api.permissions.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "用户状态重置器")
@Data
// 定义接口请求或返回数据的传输结构
public class EnableSwitchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 8477475476294666602L;

    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "用户状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enable;
}
