package io.crest.api.permissions.variable.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class SysVariableValueDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sysVariableId;
    private String value;
    private String valueDesc;
    private String begin;
    private String end;
}
