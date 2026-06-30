package io.crest.system.sso;

import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

// 定义单点登录相关的业务模型和处理入口
public enum SsoProviderType {
    OIDC_GENERIC(true, false),
    CASDOOR(true, false);

    private final boolean oidc;
    private final boolean enterpriseApp;

    SsoProviderType(boolean oidc, boolean enterpriseApp) {
        this.oidc = oidc;
        this.enterpriseApp = enterpriseApp;
    }

    // 判断当前类型是否满足业务分类
    public boolean isOidc() {
        return oidc;
    }

    // 判断当前类型是否满足业务分类
    public boolean isEnterpriseApp() {
        return enterpriseApp;
    }

    // 读取配置并返回当前功能所需参数
    public static SsoProviderType fromConfig(String value) {
        if (StringUtils.isBlank(value)) {
            return OIDC_GENERIC;
        }
        for (SsoProviderType type : values()) {
            if (Strings.CI.equals(type.name(), value.trim())) {
                return type;
            }
        }
        CrestException.throwException("不支持的身份提供方类型：" + value);
        return OIDC_GENERIC;
    }
}
