package io.crest.api.permissions.dataset.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class Item {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String account;
}
