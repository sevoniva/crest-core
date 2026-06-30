package io.crest.api.permissions.setting.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Schema(description = "设置项VO")
@Data
@NoArgsConstructor
// 定义页面展示或接口返回的数据结构
public class PerSettingItemVO implements Serializable {
    @Schema(description = "key")
    private String pkey;
    @Schema(description = "value")
    private String pval;
    @Schema(description = "类型")
    private String type;
    @Schema(description = "顺序")
    private Integer sort;
}
