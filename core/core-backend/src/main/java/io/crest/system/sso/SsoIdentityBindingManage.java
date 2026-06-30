package io.crest.system.sso;

import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
// 定义单点登录相关的业务模型和处理入口
public class SsoIdentityBindingManage {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private Environment environment;

    @Transactional
    // 保存或更新当前业务绑定关系
    public void upsertProvider(Long providerId, SsoProviderType providerType, String providerName) {
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update("""
                UPDATE core_auth_sso_provider
                SET provider_type = ?, name = ?, enabled = ?, update_time = ?
                WHERE id = ?
                """, providerType.name(), StringUtils.defaultIfBlank(providerName, "统一身份认证"), true, now, providerId);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_auth_sso_provider(id, provider_key, provider_type, name, enabled, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                providerId,
                providerId == 1L ? "default" : "provider-" + providerId,
                providerType.name(),
                StringUtils.defaultIfBlank(providerName, "统一身份认证"),
                true,
                now,
                now);
    }

    // 根据外部身份查询绑定用户编号
    public Long boundUserId(SsoIdentityProfile profile) {
        List<Long> bySubject = jdbcTemplate.queryForList("""
                SELECT user_id FROM core_auth_sso_identity_binding
                WHERE provider_id = ? AND external_subject = ?
                """.transform(this::limitOne), Long.class, profile.getProviderId(), profile.getExternalSubject());
        if (!bySubject.isEmpty()) {
            return bySubject.get(0);
        }
        if (StringUtils.isBlank(profile.getUnionId())) {
            return null;
        }
        List<Long> byUnion = jdbcTemplate.queryForList("""
                SELECT user_id FROM core_auth_sso_identity_binding
                WHERE provider_id = ? AND union_id = ?
                """.transform(this::limitOne), Long.class, profile.getProviderId(), profile.getUnionId());
        return byUnion.isEmpty() ? null : byUnion.get(0);
    }

    @Transactional
    // 保存或更新当前业务绑定关系
    public void upsert(Long userId, SsoIdentityProfile profile) {
        long now = System.currentTimeMillis();
        int updated = updateIdentityBinding("provider_id = ? AND external_subject = ?",
                userId, profile, now, profile.getProviderId(), profile.getExternalSubject());
        if (updated == 0 && StringUtils.isNotBlank(profile.getUnionId())) {
            updated = updateIdentityBinding("provider_id = ? AND union_id = ?",
                    userId, profile, now, profile.getProviderId(), profile.getUnionId());
        }
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_auth_sso_identity_binding(id, user_id, provider_id, provider_type, external_subject,
                    account, display_name, email, union_id, last_login_time, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                IDUtils.snowID(),
                userId,
                profile.getProviderId(),
                profile.getProviderType().name(),
                profile.getExternalSubject(),
                profile.getAccount(),
                profile.getName(),
                profile.getEmail(),
                StringUtils.trimToNull(profile.getUnionId()),
                now,
                now,
                now);
    }

    // SSO 绑定表存在 provider+subject/union 唯一约束，先更新再插入可避免数据库方言差异。
    private int updateIdentityBinding(String predicate, Long userId, SsoIdentityProfile profile, long now, Object... predicateArgs) {
        Object[] args = new Object[predicateArgs.length + 9];
        args[0] = userId;
        args[1] = profile.getProviderType().name();
        args[2] = profile.getExternalSubject();
        args[3] = profile.getAccount();
        args[4] = profile.getName();
        args[5] = profile.getEmail();
        args[6] = StringUtils.trimToNull(profile.getUnionId());
        args[7] = now;
        args[8] = now;
        System.arraycopy(predicateArgs, 0, args, 9, predicateArgs.length);
        return jdbcTemplate.update("""
                UPDATE core_auth_sso_identity_binding
                SET user_id = ?,
                    provider_type = ?,
                    external_subject = ?,
                    account = ?,
                    display_name = ?,
                    email = ?,
                    union_id = ?,
                    last_login_time = ?,
                    update_time = ?
                WHERE %s
                """.formatted(predicate), args);
    }

    // SSO 绑定查询按元数据库方言生成单条限制语法
    private String limitOne(String sql) {
        return dialect().limitOne(sql.stripTrailing());
    }

    // SSO 绑定 SQL 统一从环境解析当前元数据库方言
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }
}
