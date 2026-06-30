package io.crest.extensions.datasource.api;

import io.crest.extensions.datasource.vo.PluginDatasourceVO;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface PluginManageApi {
    List<PluginDatasourceVO> queryPluginDs();
}
