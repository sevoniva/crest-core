package io.crest.api.permissions.login.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class AccountLockStatus implements Serializable {
    @Serial
    private static final long serialVersionUID = 8310029430986389948L;

    private Boolean locked = false;

    private String account;

    private Long unlockTime;

    private Integer relieveTimes;

    private Integer remainderTimes;
}
