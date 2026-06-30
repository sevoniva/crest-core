package io.crest.api.permissions.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "用户解绑器")
@Data
// 定义接口请求或返回数据的传输结构
public class UnmountUserRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1361046648092771178L;
    @Schema(description = "角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long rid;
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long uid;
}
