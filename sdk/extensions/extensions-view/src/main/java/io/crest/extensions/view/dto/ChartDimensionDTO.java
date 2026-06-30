package io.crest.extensions.view.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartDimensionDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String value;
}
