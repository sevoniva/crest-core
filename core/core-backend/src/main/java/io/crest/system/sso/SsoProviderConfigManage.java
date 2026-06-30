package io.crest.system.sso;

import io.crest.api.system.vo.SsoConfigVO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
// 定义单点登录相关的业务模型和处理入口
public class SsoProviderConfigManage {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public static SsoProviderConfig defaultProvider(SsoConfigVO config, String encryptedSecret) {
        SsoProviderConfig provider = new SsoProviderConfig();
        provider.setProviderId(1L);
        provider.setProviderKey("default");
        provider.setProviderType(SsoProviderType.fromConfig(config.getProviderType()));
        provider.setName(StringUtils.defaultIfBlank(config.getProviderName(), "统一身份认证"));
        provider.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        provider.setClientId(config.getClientId());
        provider.setClientSecret(encryptedSecret);
        provider.setAuthorizationEndpoint(config.getAuthorizationEndpoint());
        provider.setTokenEndpoint(config.getTokenEndpoint());
        provider.setUserInfoEndpoint(config.getUserInfoEndpoint());
        provider.setIssuer(config.getIssuer());
        provider.setScope(config.getScope());
        provider.setRedirectUri(config.getRedirectUri());
        provider.setUserIdAttribute(config.getUserIdAttribute());
        provider.setAccountAttribute(config.getAccountAttribute());
        provider.setNameAttribute(config.getNameAttribute());
        provider.setEmailAttribute(config.getEmailAttribute());
        provider.setUnionIdAttribute(config.getUnionIdAttribute());
        provider.setAutoCreateUser(Boolean.TRUE.equals(config.getAutoCreateUser()));
        provider.setRequireHttps(Boolean.TRUE.equals(config.getRequireHttps()));
        return provider;
    }

    @Transactional
    // 保存或更新当前业务绑定关系
    public void upsert(SsoProviderConfig provider) {
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update("""
                UPDATE core_auth_sso_provider
                SET provider_type = ?,
                    name = ?,
                    enabled = ?,
                    client_id = ?,
                    client_secret = ?,
                    authorization_endpoint = ?,
                    token_endpoint = ?,
                    user_info_endpoint = ?,
                    issuer = ?,
                    scope = ?,
                    redirect_uri = ?,
                    user_id_attribute = ?,
                    account_attribute = ?,
                    name_attribute = ?,
                    email_attribute = ?,
                    union_id_attribute = ?,
                    auto_create_user = ?,
                    require_https = ?,
                    update_time = ?
                WHERE id = ?
                """,
                provider.getProviderType().name(),
                provider.getName(),
                provider.getEnabled(),
                provider.getClientId(),
                provider.getClientSecret(),
                provider.getAuthorizationEndpoint(),
                provider.getTokenEndpoint(),
                provider.getUserInfoEndpoint(),
                provider.getIssuer(),
                provider.getScope(),
                provider.getRedirectUri(),
                provider.getUserIdAttribute(),
                provider.getAccountAttribute(),
                provider.getNameAttribute(),
                provider.getEmailAttribute(),
                provider.getUnionIdAttribute(),
                provider.getAutoCreateUser(),
                provider.getRequireHttps(),
                now,
                provider.getProviderId());
        if (updated > 0) {
            return;
        }
        // 新增路径只在首次启用 SSO 时执行，更新路径避免依赖 MySQL 专有 upsert 语法。
        jdbcTemplate.update("""
                INSERT INTO core_auth_sso_provider(id, provider_key, provider_type, name, enabled, client_id, client_secret,
                    authorization_endpoint, token_endpoint, user_info_endpoint, issuer, scope, redirect_uri,
                    user_id_attribute, account_attribute, name_attribute, email_attribute, union_id_attribute,
                    auto_create_user, require_https, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                provider.getProviderId(),
                provider.getProviderKey(),
                provider.getProviderType().name(),
                provider.getName(),
                provider.getEnabled(),
                provider.getClientId(),
                provider.getClientSecret(),
                provider.getAuthorizationEndpoint(),
                provider.getTokenEndpoint(),
                provider.getUserInfoEndpoint(),
                provider.getIssuer(),
                provider.getScope(),
                provider.getRedirectUri(),
                provider.getUserIdAttribute(),
                provider.getAccountAttribute(),
                provider.getNameAttribute(),
                provider.getEmailAttribute(),
                provider.getUnionIdAttribute(),
                provider.getAutoCreateUser(),
                provider.getRequireHttps(),
                now,
                now);
    }
}
