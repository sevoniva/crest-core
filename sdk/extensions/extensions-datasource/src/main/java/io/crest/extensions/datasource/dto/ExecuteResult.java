package io.crest.extensions.datasource.dto;

import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ExecuteResult {

    private int count;

    private List<String> generatedKeys;
}
