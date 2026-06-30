package io.crest.extensions.datasource.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class DsTypeDTO {
    private String type;
    private String name;

    private String catalog;
    private String prefix;
    private String suffix;
}
