package io.crest.api.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema(description = "分享UUID编辑器")
@Data
// 定义模块接口契约和数据传输结构
public class ShareUuidEditor implements Serializable {

    @Schema(description = "资源ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long resourceId;
    @Schema(description = "分享UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uuid;
}
