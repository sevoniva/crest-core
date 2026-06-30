package io.crest.api.permissions.auth.dto;

import io.crest.api.permissions.auth.vo.PermissionItem;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class PermissionBO extends PermissionItem {

    private Long resourceId;
}
