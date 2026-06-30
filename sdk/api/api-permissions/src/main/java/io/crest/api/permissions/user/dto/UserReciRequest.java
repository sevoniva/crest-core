package io.crest.api.permissions.user.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class UserReciRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -2165057126540959376L;

    private List<Long> uidList;

    private List<Long> ridList;
}
