package io.crest.api.dataset.engine;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class SQLFunctionDTO {
    private String name;
    private String func;
    private int type;
    private String desc;
    private boolean isCustom;
}
