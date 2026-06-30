package io.crest.api.permissions.role.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class RoleCopyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1631759805936434870L;

    private Long copyId;
    private String name;
    private String desc;
}
