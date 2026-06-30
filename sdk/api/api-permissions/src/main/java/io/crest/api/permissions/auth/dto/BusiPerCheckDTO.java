package io.crest.api.permissions.auth.dto;

import io.crest.constant.AuthEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class BusiPerCheckDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = -6047004531129863548L;

    private Long id;

    private AuthEnum authEnum;
}
