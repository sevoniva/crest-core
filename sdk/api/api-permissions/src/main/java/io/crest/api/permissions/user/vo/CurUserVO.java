package io.crest.api.permissions.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "当前登录人信息VO")
@Data
// 定义页面展示或接口返回的数据结构
public class CurUserVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1190164294672439979L;
    @JsonSerialize(using= ToStringSerializer.class)
    @Schema(description = "ID")
    private Long id;
    @Schema(description = "名称")
    private String name;
    @JsonSerialize(using= ToStringSerializer.class)
    @Schema(description = "组织ID")
    private Long oid;
    @Schema(description = "语言")
    private String language;
    @Schema(description = "是否管理员")
    private Boolean admin;
    @Schema(description = "是否允许进入管理后台")
    private Boolean backendAccess;
}
