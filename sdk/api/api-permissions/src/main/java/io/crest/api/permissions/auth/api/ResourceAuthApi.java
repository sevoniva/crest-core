package io.crest.api.permissions.auth.api;

import io.crest.api.permissions.auth.dto.ResourcePermissionRequest;
import io.crest.api.permissions.auth.vo.ResourcePermissionVO;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface ResourceAuthApi {

    List<ResourcePermissionVO> queryResourcePermission(ResourcePermissionRequest request);
}
