package io.crest.api.font.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class FontDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 字体名称
     */
    private String name;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件转换名称
     */
    private String fileTransName;

    /**
     * 是否默认
     */
    private Boolean isDefault;
    private Long updateTime;
    private Boolean isBuiltin;
    private Double size;
    private String sizeType;
}

