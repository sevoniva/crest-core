package io.crest.api.permissions.role.dto;

import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Schema(description = "角色过滤器")
@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class RoleRequest extends KeywordRequest {


    @Serial
    private static final long serialVersionUID = 7354856549096378406L;
    @Schema(description = "用户ID")
    private Long uid;
}
