package io.crest.api.permissions.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "组外用户绑定器")
@Data
// 定义接口请求或返回数据的传输结构
public class MountExternalUserRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -1682136323964916544L;
    @Schema(description = "角色ID")
    private Long rid;
    @Schema(description = "组外用户ID")
    private Long uid;
}
