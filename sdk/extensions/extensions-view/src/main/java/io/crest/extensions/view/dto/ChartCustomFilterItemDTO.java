package io.crest.extensions.view.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartCustomFilterItemDTO implements Serializable {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;
    private String term;
    private String value;
    private String filterDateFormat;
}
