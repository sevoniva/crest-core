package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ColumnPermissions {
    private Boolean enable;
    private List<ColumnPermissionItem> columns;
}
