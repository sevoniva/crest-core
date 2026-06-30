package io.crest.api.permissions.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "角色项")
@Data
// 定义页面展示或接口返回的数据结构
public class UserGridRoleItem {

    @Schema(description = "角色ID")
    private Long id;
    @Schema(description = "角色名称")
    private String name;
}
