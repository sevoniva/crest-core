package io.crest.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "关键紫过滤器")
@Data
// 定义接口请求或返回数据的传输结构
public class KeywordRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -3038086304525253475L;
    @Schema(description = "关键字")
    private String keyword;
}
