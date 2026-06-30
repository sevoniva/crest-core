package io.crest.datasource.dto.es;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
// 定义接口请求或返回数据的传输结构
public class RequestWithCursor extends Request {
    private String cursor;
}
