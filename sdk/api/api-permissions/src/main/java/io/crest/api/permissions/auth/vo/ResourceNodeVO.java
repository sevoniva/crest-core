package io.crest.api.permissions.auth.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data
// 定义页面展示或接口返回的数据结构
public class ResourceNodeVO implements Serializable {

    @JsonSerialize(using= ToStringSerializer.class)
    private Long id;

    private String name;
}
