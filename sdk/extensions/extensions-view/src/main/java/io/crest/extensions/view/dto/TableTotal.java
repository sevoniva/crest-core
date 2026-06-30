package io.crest.extensions.view.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class TableTotal {
    private TableTotalCfg row;
    private TableTotalCfg col;
}
