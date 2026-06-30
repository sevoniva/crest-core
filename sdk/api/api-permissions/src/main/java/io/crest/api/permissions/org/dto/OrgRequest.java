package io.crest.api.permissions.org.dto;

import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;


@Schema(description = "组织列表过滤器")
@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class OrgRequest extends KeywordRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1697526057837588192L;
    @Schema(description = "是否降序", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean desc = true;
}
