package io.crest.api.permissions.auth.dto;

import io.crest.api.permissions.auth.vo.PermissionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
@EqualsAndHashCode(callSuper = true)
@Schema(description = "菜单权限构造器")
@Data
// 定义接口请求或返回数据的传输结构
public class MenuTargetPerCreator extends TargetPerCreator{

    @Schema(description = "权限集合")
    private List<PermissionItem> permissions;
}
