package io.crest.extensions.datafilling.api;

import io.crest.extensions.datafilling.vo.PluginDataFillingVO;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface DfPluginManageApi {
    List<PluginDataFillingVO> queryPluginDf();
}
