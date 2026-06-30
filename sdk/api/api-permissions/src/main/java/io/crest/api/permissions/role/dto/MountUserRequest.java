package io.crest.api.permissions.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Schema(description = "用户绑定器")
@Data
// 定义接口请求或返回数据的传输结构
public class MountUserRequest implements Serializable {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long rid;
    @Schema(description = "用户ID集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> uids;
}
