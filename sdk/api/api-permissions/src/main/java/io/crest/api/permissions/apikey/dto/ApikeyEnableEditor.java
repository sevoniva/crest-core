package io.crest.api.permissions.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema(description = "状态切换器")
@Data
// 定义接口请求或返回数据的传输结构
public class ApikeyEnableEditor implements Serializable {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "状态", defaultValue = "false")
    private Boolean enable = false;
}
