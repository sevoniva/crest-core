package io.crest.datasource.dto.es;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class Request {
    private String query;
    private Integer fetch_size = 10000;
    private boolean field_multi_value_leniency = true;
}
