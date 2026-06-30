package io.crest.system.sso;

import lombok.Data;

@Data
// 定义单点登录相关的业务模型和处理入口
public class SsoIdentityProfile {
    private Long providerId;
    private SsoProviderType providerType;
    private String externalSubject;
    private String account;
    private String name;
    private String email;
    private String unionId;
}
