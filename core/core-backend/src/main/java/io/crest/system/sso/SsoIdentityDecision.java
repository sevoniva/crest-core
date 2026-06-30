package io.crest.system.sso;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
// 定义单点登录相关的业务模型和处理入口
public class SsoIdentityDecision {
    private SsoIdentityAction action;
    private Long userId;
    private SsoIdentityProfile profile;
}
