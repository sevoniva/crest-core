package io.crest.api.permissions.relation.api;

import io.crest.exception.CrestException;

// 定义模块接口契约和数据传输结构
public interface RelationApi {
    Long getDsResource(Long id);

    Long datasetResource(Long id);

    void checkAuth() throws CrestException;
}
