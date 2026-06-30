package io.crest.extensions.view.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
// 定义接口请求或返回数据的传输结构
public class DynamicValueDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;
    private BigDecimal value;
    private String stringValue;
}
