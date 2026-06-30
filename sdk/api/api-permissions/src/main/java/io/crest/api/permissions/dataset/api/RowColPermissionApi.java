package io.crest.api.permissions.dataset.api;

import io.crest.api.permissions.dataset.vo.RowColPermissionItem;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface RowColPermissionApi {

    List<RowColPermissionItem> query(List<Long> datasetIds);
}
