package io.crest.api.export;

import io.swagger.v3.oas.annotations.Hidden;

import java.util.HashMap;

@Hidden
// 定义模块接口契约和数据传输结构
public interface BaseExportApi {

    void addTask(String exportFromId, String exportFromType, HashMap<String, Object> request, Long userId, Long org);


}
