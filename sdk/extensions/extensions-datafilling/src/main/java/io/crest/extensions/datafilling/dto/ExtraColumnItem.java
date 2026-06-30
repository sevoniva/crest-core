package io.crest.extensions.datafilling.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
// 定义接口请求或返回数据的传输结构
public class ExtraColumnItem {
    private String fieldName;
    private String displayName;
}
