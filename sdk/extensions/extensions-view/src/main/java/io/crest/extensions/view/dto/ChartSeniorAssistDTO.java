package io.crest.extensions.view.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartSeniorAssistDTO {
    private String name;
    private String field;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;
    private String summary;
    private String axis;
    @JsonProperty("yAxisType")
    private String yAxisType;
    private String value;
    private String lineType;
    private String color;
    private ChartViewFieldDTO curField;
    private String fontSize;
}
