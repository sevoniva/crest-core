package io.crest.system.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.system.request.SsoConfigRequest;
import io.crest.api.system.vo.SettingItemVO;
import io.crest.api.system.vo.SsoConfigVO;
import io.crest.api.system.vo.SsoStatusVO;
import io.crest.auth.bo.TokenUserBO;
import io.crest.auth.vo.TokenVO;
import io.crest.constant.AuthConstant;
import io.crest.constant.CacheConstant;
import io.crest.exception.CrestException;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.substitute.permissions.user.model.CrestUser;
import io.crest.system.sso.SsoClaimMapper;
import io.crest.system.sso.SsoEndpointPolicy;
import io.crest.system.sso.SsoIdentityProfile;
import io.crest.system.sso.SsoLoginIdentityManage;
import io.crest.system.sso.SsoProviderConfigManage;
import io.crest.system.sso.SsoProviderType;
import io.crest.utils.AesUtils;
import io.crest.utils.CacheUtils;
import io.crest.utils.HttpClientConfig;
import io.crest.utils.HttpClientUtil;
import io.crest.utils.IDUtils;
import io.crest.utils.JsonUtil;
import io.crest.utils.SignedTokenUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 单点登录管理服务，负责配置保存、OIDC 登录跳转、回调换票和本地用户身份解析
 */
@Component
public class SsoManage {

    /**
     * 系统参数表中的 SSO 配置键前缀
     */
    private static final String KEY_PREFIX = "sso.";
    /**
     * 未配置供应商名称时展示的默认名称
     */
    private static final String DEFAULT_PROVIDER_NAME = "统一身份认证";
    /**
     * OIDC 默认授权范围
     */
    private static final String DEFAULT_SCOPE = "openid profile email";
    /**
     * 默认用户唯一标识声明字段
     */
    private static final String DEFAULT_USER_ID_ATTRIBUTE = "sub";
    /**
     * 默认账号声明字段
     */
    private static final String DEFAULT_ACCOUNT_ATTRIBUTE = "preferred_username";
    /**
     * 默认显示名声明字段
     */
    private static final String DEFAULT_NAME_ATTRIBUTE = "name";
    /**
     * 默认邮箱声明字段
     */
    private static final String DEFAULT_EMAIL_ATTRIBUTE = "email";
    /**
     * SSO 登录完成后的默认前端落点
     */
    private static final String DEFAULT_REDIRECT = "/workbranch/index";

    /**
     * 系统参数服务，负责读取和保存 SSO 配置项
     */
    @Resource
    private SysParameterManage sysParameterManage;

    /**
     * SSO 身份解析服务，负责绑定或创建本地用户
     */
    @Resource
    private SsoLoginIdentityManage ssoLoginIdentityManage;

    /**
     * SSO 供应商配置服务，保持系统参数和供应商表同步
     */
    @Resource
    private SsoProviderConfigManage ssoProviderConfigManage;

    /**
     * 平台权限服务，用于生成本地令牌时解析默认组织
     */
    @Resource
    private PlatformPermissionManage platformPermissionManage;

    /**
     * 返回登录页需要的 SSO 开关和展示状态，不暴露敏感配置
     */
    public SsoStatusVO status() {
        SsoConfigVO config = config(null);
        SsoStatusVO vo = new SsoStatusVO();
        vo.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        vo.setProviderName(config.getProviderName());
        vo.setLoginButtonText("单点登录");
        vo.setAllowLocalLogin(Boolean.TRUE.equals(config.getAllowLocalLogin()));
        return vo;
    }

    /**
     * 读取 SSO 配置并补齐默认值，客户端密钥只返回是否已配置
     */
    public SsoConfigVO config(HttpServletRequest request) {
        Map<String, String> values = sysParameterManage.groupVal(KEY_PREFIX);
        SsoConfigVO vo = new SsoConfigVO();
        vo.setEnabled(bool(values, "enabled", false));
        vo.setProviderName(text(values, "providerName", DEFAULT_PROVIDER_NAME));
        vo.setClientId(text(values, "clientId", ""));
        vo.setAuthorizationEndpoint(text(values, "authorizationEndpoint", ""));
        vo.setTokenEndpoint(text(values, "tokenEndpoint", ""));
        vo.setUserInfoEndpoint(text(values, "userInfoEndpoint", ""));
        vo.setIssuer(text(values, "issuer", ""));
        vo.setProviderType(text(values, "providerType", derivedProviderType(vo).name()));
        vo.setScope(text(values, "scope", DEFAULT_SCOPE));
        vo.setRedirectUri(text(values, "redirectUri", ""));
        vo.setUserIdAttribute(text(values, "userIdAttribute", DEFAULT_USER_ID_ATTRIBUTE));
        vo.setAccountAttribute(text(values, "accountAttribute", DEFAULT_ACCOUNT_ATTRIBUTE));
        vo.setNameAttribute(text(values, "nameAttribute", DEFAULT_NAME_ATTRIBUTE));
        vo.setEmailAttribute(text(values, "emailAttribute", DEFAULT_EMAIL_ATTRIBUTE));
        vo.setUnionIdAttribute(text(values, "unionIdAttribute", ""));
        vo.setAutoCreateUser(bool(values, "autoCreateUser", true));
        vo.setAllowLocalLogin(bool(values, "allowLocalLogin", true));
        vo.setRequireHttps(bool(values, "requireHttps", true));
        vo.setLogoutRedirectUrl(text(values, "logoutRedirectUrl", ""));
        String encryptedSecret = text(values, "clientSecret", "");
        vo.setClientSecret("");
        vo.setSecretConfigured(StringUtils.isNotBlank(encryptedSecret));
        vo.setCallbackUrl(callbackUrl(request, vo));
        return vo;
    }

    /**
     * 判断当前环境是否允许本地账号登录
     */
    public boolean localLoginAllowed() {
        SsoConfigVO config = config(null);
        return !Boolean.TRUE.equals(config.getEnabled()) || Boolean.TRUE.equals(config.getAllowLocalLogin());
    }

    /**
     * 保存 SSO 配置，客户端密钥加密后落库并同步默认供应商配置
     */
    @Transactional
    public void save(SsoConfigRequest request) {
        Map<String, String> existing = sysParameterManage.groupVal(KEY_PREFIX);
        String encryptedSecret = text(existing, "clientSecret", "");
        if (StringUtils.isNotBlank(request.getClientSecret())) {
            encryptedSecret = AesUtils.aesEncrypt(request.getClientSecret().trim()).toString();
        }
        SsoConfigVO config = normalize(request);
        validateConfig(config, encryptedSecret);
        sysParameterManage.saveGroup(settingItems(config, encryptedSecret), KEY_PREFIX);
        ssoProviderConfigManage.upsert(SsoProviderConfigManage.defaultProvider(config, encryptedSecret));
    }

    /**
     * 仅校验待保存配置，不写入系统参数
     */
    public void validate(SsoConfigRequest request) {
        Map<String, String> existing = sysParameterManage.groupVal(KEY_PREFIX);
        String encryptedSecret = text(existing, "clientSecret", "");
        validateConfig(normalize(request), StringUtils.defaultIfBlank(request.getClientSecret(), encryptedSecret));
    }

    /**
     * 发起 SSO 登录，生成 state 和 nonce 后跳转到授权端点
     */
    public void login(String redirect, HttpServletRequest request, HttpServletResponse response) {
        SsoConfigVO config = runtimeConfig(request);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            CrestException.throwException("单点登录未启用");
        }
        String state = IDUtils.randomID(40);
        String nonce = IDUtils.randomID(32);
        String target = normalizeRedirect(redirect);
        CacheUtils.put(CacheConstant.UserCacheConstant.SSO_STATE_CACHE, state,
                new SsoState(target, nonce), 10L, TimeUnit.MINUTES);
        try {
            URIBuilder builder = new URIBuilder(config.getAuthorizationEndpoint())
                    .addParameter("response_type", "code")
                    .addParameter("client_id", config.getClientId())
                    .addParameter("redirect_uri", callbackUrl(request, config))
                    .addParameter("scope", config.getScope())
                    .addParameter("state", state)
                    .addParameter("nonce", nonce);
            response.sendRedirect(builder.build().toString());
        } catch (Exception e) {
            CrestException.throwException("单点登录跳转地址生成失败：" + e.getMessage());
        }
    }

    /**
     * 处理 SSO 回调，完成 code 换 token、用户信息解析和一次性登录票据生成
     */
    public void callback(String code, String state, String error, String errorDescription,
                         HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.isNotBlank(error)) {
            redirectError(request, response, StringUtils.defaultIfBlank(errorDescription, error));
            return;
        }
        if (StringUtils.isAnyBlank(code, state)) {
            redirectError(request, response, "单点登录回调缺少 code 或 state");
            return;
        }
        Object cached = CacheUtils.get(CacheConstant.UserCacheConstant.SSO_STATE_CACHE, state);
        CacheUtils.keyRemove(CacheConstant.UserCacheConstant.SSO_STATE_CACHE, state);
        if (!(cached instanceof SsoState ssoState)) {
            redirectError(request, response, "单点登录会话已过期，请重新登录");
            return;
        }
        try {
            SsoConfigVO config = runtimeConfig(request);
            Map<String, Object> token = exchangeToken(code, request, config);
            String accessToken = asText(token.get("access_token"));
            if (StringUtils.isBlank(accessToken)) {
                throw new IllegalStateException("令牌响应中缺少 access_token");
            }
            Map<String, Object> claims = identityClaims(config, token, accessToken);
            SsoIdentityProfile profile = profile(config, claims);
            CrestUser user = ssoLoginIdentityManage.resolve(profile, Boolean.TRUE.equals(config.getAutoCreateUser()), config.getProviderName());
            TokenVO tokenVO = generate(user);
            String ticket = IDUtils.randomID(48);
            CacheUtils.put(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, ticket, tokenVO, 1L, TimeUnit.MINUTES);
            redirectTicket(request, response, ticket, ssoState.redirect());
        } catch (Exception e) {
            redirectError(request, response, e.getMessage());
        }
    }

    /**
     * 消费前端回传的一次性 SSO 票据，成功后立即删除缓存票据
     */
    public TokenVO token(String ticket) {
        Object cached = CacheUtils.get(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, ticket);
        CacheUtils.keyRemove(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, ticket);
        if (cached instanceof TokenVO tokenVO) {
            return tokenVO;
        }
        CrestException.throwException("单点登录票据无效或已过期");
        return null;
    }

    /**
     * 获取运行态配置，启用时会校验完整性并解密客户端密钥
     */
    private SsoConfigVO runtimeConfig(HttpServletRequest request) {
        SsoConfigVO config = config(request);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return config;
        }
        Map<String, String> values = sysParameterManage.groupVal(KEY_PREFIX);
        String encryptedSecret = text(values, "clientSecret", "");
        validateConfig(config, encryptedSecret);
        try {
            config.setClientSecret(AesUtils.aesDecrypt(encryptedSecret).toString());
        } catch (Exception e) {
            CrestException.throwException("单点登录客户端密钥无法解密，请检查加密配置");
        }
        return config;
    }

    /**
     * 规范化配置请求，统一默认值、空白字符串和布尔开关语义
     */
    private SsoConfigVO normalize(SsoConfigRequest request) {
        SsoConfigVO vo = new SsoConfigVO();
        vo.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        vo.setProviderName(StringUtils.defaultIfBlank(request.getProviderName(), DEFAULT_PROVIDER_NAME).trim());
        vo.setProviderType(SsoProviderType.fromConfig(request.getProviderType()).name());
        vo.setClientId(StringUtils.trimToEmpty(request.getClientId()));
        vo.setAuthorizationEndpoint(StringUtils.trimToEmpty(request.getAuthorizationEndpoint()));
        vo.setTokenEndpoint(StringUtils.trimToEmpty(request.getTokenEndpoint()));
        vo.setUserInfoEndpoint(StringUtils.trimToEmpty(request.getUserInfoEndpoint()));
        vo.setIssuer(StringUtils.trimToEmpty(request.getIssuer()));
        vo.setScope(StringUtils.defaultIfBlank(request.getScope(), DEFAULT_SCOPE).trim());
        vo.setRedirectUri(StringUtils.trimToEmpty(request.getRedirectUri()));
        vo.setUserIdAttribute(StringUtils.defaultIfBlank(request.getUserIdAttribute(), DEFAULT_USER_ID_ATTRIBUTE).trim());
        vo.setAccountAttribute(StringUtils.defaultIfBlank(request.getAccountAttribute(), DEFAULT_ACCOUNT_ATTRIBUTE).trim());
        vo.setNameAttribute(StringUtils.defaultIfBlank(request.getNameAttribute(), DEFAULT_NAME_ATTRIBUTE).trim());
        vo.setEmailAttribute(StringUtils.defaultIfBlank(request.getEmailAttribute(), DEFAULT_EMAIL_ATTRIBUTE).trim());
        vo.setUnionIdAttribute(StringUtils.trimToEmpty(request.getUnionIdAttribute()));
        vo.setAutoCreateUser(request.getAutoCreateUser() == null || request.getAutoCreateUser());
        vo.setAllowLocalLogin(request.getAllowLocalLogin() == null || request.getAllowLocalLogin());
        vo.setRequireHttps(request.getRequireHttps() == null || request.getRequireHttps());
        vo.setLogoutRedirectUrl(StringUtils.trimToEmpty(request.getLogoutRedirectUrl()));
        return vo;
    }

    /**
     * 校验启用状态下的必填项和端点安全策略
     */
    private void validateConfig(SsoConfigVO config, String secret) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        if (StringUtils.isAnyBlank(config.getProviderName(), config.getClientId(), secret,
                config.getAuthorizationEndpoint(), config.getTokenEndpoint(), config.getUserInfoEndpoint(),
                config.getScope(), config.getUserIdAttribute(), config.getAccountAttribute())) {
            CrestException.throwException("单点登录配置不完整");
        }
        validateEndpoint(config.getAuthorizationEndpoint(), config.getRequireHttps(), "授权端点");
        validateEndpoint(config.getTokenEndpoint(), config.getRequireHttps(), "令牌端点");
        validateEndpoint(config.getUserInfoEndpoint(), config.getRequireHttps(), "用户信息端点");
        if (StringUtils.isNotBlank(config.getRedirectUri())) {
            validateEndpoint(config.getRedirectUri(), config.getRequireHttps(), "回调地址");
        }
        if (StringUtils.isNotBlank(config.getLogoutRedirectUrl())) {
            validateEndpoint(config.getLogoutRedirectUrl(), config.getRequireHttps(), "退出跳转地址");
        }
    }

    /**
     * 按配置要求校验端点地址，HTTPS 要求由配置开关控制
     */
    private void validateEndpoint(String value, Boolean requireHttps, String name) {
        SsoEndpointPolicy.validateEndpoint(value, requireHttps, name);
    }

    /**
     * 将配置对象转换为系统参数保存项，并保持固定排序
     */
    private List<SettingItemVO> settingItems(SsoConfigVO config, String encryptedSecret) {
        List<SettingItemVO> items = new ArrayList<>();
        add(items, "enabled", config.getEnabled(), 1);
        add(items, "providerName", config.getProviderName(), 2);
        add(items, "providerType", config.getProviderType(), 3);
        add(items, "clientId", config.getClientId(), 4);
        add(items, "clientSecret", encryptedSecret, 5);
        add(items, "authorizationEndpoint", config.getAuthorizationEndpoint(), 6);
        add(items, "tokenEndpoint", config.getTokenEndpoint(), 7);
        add(items, "userInfoEndpoint", config.getUserInfoEndpoint(), 8);
        add(items, "issuer", config.getIssuer(), 9);
        add(items, "scope", config.getScope(), 10);
        add(items, "redirectUri", config.getRedirectUri(), 11);
        add(items, "userIdAttribute", config.getUserIdAttribute(), 12);
        add(items, "accountAttribute", config.getAccountAttribute(), 13);
        add(items, "nameAttribute", config.getNameAttribute(), 14);
        add(items, "emailAttribute", config.getEmailAttribute(), 15);
        add(items, "unionIdAttribute", config.getUnionIdAttribute(), 16);
        add(items, "autoCreateUser", config.getAutoCreateUser(), 17);
        add(items, "allowLocalLogin", config.getAllowLocalLogin(), 18);
        add(items, "requireHttps", config.getRequireHttps(), 19);
        add(items, "logoutRedirectUrl", config.getLogoutRedirectUrl(), 20);
        return items;
    }

    /**
     * 添加单个系统参数项，空值统一保存为空字符串
     */
    private void add(List<SettingItemVO> items, String key, Object value, int sort) {
        SettingItemVO item = new SettingItemVO();
        item.setPkey(KEY_PREFIX + key);
        item.setPval(value == null ? "" : value.toString());
        item.setType("text");
        item.setSort(sort);
        items.add(item);
    }

    /**
     * 使用授权码向令牌端点换取 token 响应
     */
    private Map<String, Object> exchangeToken(String code, HttpServletRequest request, SsoConfigVO config) {
        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", callbackUrl(request, config),
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret()
        );
        HttpClientConfig clientConfig = new HttpClientConfig();
        clientConfig.addHeader("Accept", "application/json");
        String response = HttpClientUtil.post(config.getTokenEndpoint(), body, clientConfig);
        Map<String, Object> result = JsonUtil.parseObject(response, new TypeReference<>() {});
        if (result == null) {
            throw new IllegalStateException("令牌端点返回内容不是有效 JSON");
        }
        return result;
    }

    /**
     * 使用 access token 调用用户信息端点
     */
    private Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken) {
        HttpClientConfig clientConfig = new HttpClientConfig();
        clientConfig.addHeader("Accept", "application/json");
        clientConfig.addHeader("Authorization", "Bearer " + accessToken);
        String response = HttpClientUtil.get(userInfoEndpoint, clientConfig);
        Map<String, Object> result = JsonUtil.parseObject(response, new TypeReference<>() {});
        if (result == null) {
            throw new IllegalStateException("用户信息端点返回内容不是有效 JSON");
        }
        return result;
    }

    /**
     * 合并 id_token、access_token 和 userinfo 中的身份声明
     */
    private Map<String, Object> identityClaims(SsoConfigVO config, Map<String, Object> token, String accessToken) {
        Map<String, Object> claims = new HashMap<>();
        mergeNonBlank(claims, jwtClaims(asText(token.get("id_token"))));
        mergeNonBlank(claims, jwtClaims(accessToken));
        try {
            mergeNonBlank(claims, fetchUserInfo(config.getUserInfoEndpoint(), accessToken));
        } catch (Exception e) {
            if (claims.isEmpty()) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return claims;
    }

    /**
     * 解析 JWT 载荷声明，无法解析时返回空声明集合
     */
    private Map<String, Object> jwtClaims(String token) {
        if (StringUtils.isBlank(token)) {
            return Map.of();
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Map.of();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> result = JsonUtil.parseObject(payload, new TypeReference<>() {});
            return result == null ? Map.of() : result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 将非空声明合并到目标集合，后写入来源可覆盖先前值
     */
    private void mergeNonBlank(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && value != null && StringUtils.isNotBlank(value.toString())) {
                target.put(key, value);
            }
        });
    }

    /**
     * 根据配置的声明字段映射生成标准 SSO 身份画像
     */
    private SsoIdentityProfile profile(SsoConfigVO config, Map<String, Object> claims) {
        return SsoClaimMapper.map(
                1L,
                providerType(config),
                claims,
                config.getUserIdAttribute(),
                config.getAccountAttribute(),
                config.getNameAttribute(),
                config.getEmailAttribute(),
                config.getUnionIdAttribute()
        );
    }

    /**
     * 获取供应商类型，优先使用显式配置，缺失时根据配置内容推断
     */
    private SsoProviderType providerType(SsoConfigVO config) {
        if (StringUtils.isNotBlank(config.getProviderType())) {
            return SsoProviderType.fromConfig(config.getProviderType());
        }
        return derivedProviderType(config);
    }

    /**
     * 根据供应商名称和 issuer 推断供应商类型
     */
    private SsoProviderType derivedProviderType(SsoConfigVO config) {
        if (Strings.CI.contains(config.getProviderName(), "casdoor")
                || Strings.CI.contains(config.getIssuer(), "casdoor")) {
            return SsoProviderType.CASDOOR;
        }
        return SsoProviderType.OIDC_GENERIC;
    }

    /**
     * 将任意声明值转换为字符串
     */
    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 为 SSO 登录用户生成本系统访问令牌
     */
    private TokenVO generate(CrestUser user) {
        TokenUserBO bo = new TokenUserBO();
        bo.setUserId(user.getId());
        bo.setDefaultOid(platformPermissionManage.defaultOrgId(user.getId()));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", bo.getUserId());
        claims.put("oid", bo.getDefaultOid());
        String token = SignedTokenUtils.sign(claims, user.getPasswordHash(), null);
        return new TokenVO(token, 0L);
    }

    /**
     * 生成回调地址，显式配置优先，否则根据代理头和请求上下文推导
     */
    private String callbackUrl(HttpServletRequest request, SsoConfigVO config) {
        if (config != null && StringUtils.isNotBlank(config.getRedirectUri())) {
            return config.getRedirectUri();
        }
        if (request == null) {
            return "/sso/callback";
        }
        String proto = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String host = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Host"), request.getServerName());
        if (!host.contains(":")) {
            String port = request.getHeader("X-Forwarded-Port");
            int serverPort = StringUtils.isNotBlank(port) ? Integer.parseInt(port) : request.getServerPort();
            boolean defaultPort = (Strings.CI.equals(proto, "https") && serverPort == 443)
                    || (Strings.CI.equals(proto, "http") && serverPort == 80);
            if (!defaultPort) {
                host += ":" + serverPort;
            }
        }
        return proto + "://" + host + request.getContextPath() + AuthConstant.CREST_API_PREFIX + "/sso/callback";
    }

    /**
     * 将一次性票据和目标地址带回前端 SSO 回调页
     */
    private void redirectTicket(HttpServletRequest request, HttpServletResponse response, String ticket, String redirect) throws IOException {
        response.sendRedirect(request.getContextPath() + "/#/sso/callback?ticket=" + encode(ticket) + "&redirect=" + encode(redirect));
    }

    /**
     * 将错误信息带回前端 SSO 回调页
     */
    private void redirectError(HttpServletRequest request, HttpServletResponse response, String message) {
        try {
            response.sendRedirect(request.getContextPath() + "/#/sso/callback?error=" + encode(StringUtils.defaultIfBlank(message, "单点登录失败")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 规范化登录后跳转地址，禁止外部 URL 进入 redirect 参数
     */
    private String normalizeRedirect(String redirect) {
        if (StringUtils.isBlank(redirect)) {
            return DEFAULT_REDIRECT;
        }
        String value = redirect.trim();
        if (Strings.CI.startsWithAny(value, "http://", "https://", "//")) {
            return DEFAULT_REDIRECT;
        }
        return Strings.CS.startsWith(value, "/") ? value : "/" + value;
    }

    /**
     * URL 编码回调参数
     */
    private String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    /**
     * 读取带前缀的文本配置项
     */
    private String text(Map<String, String> values, String key, String defaultValue) {
        return StringUtils.defaultIfBlank(values.get(KEY_PREFIX + key), defaultValue);
    }

    /**
     * 读取带前缀的布尔配置项
     */
    private Boolean bool(Map<String, String> values, String key, boolean defaultValue) {
        String value = values.get(KEY_PREFIX + key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * SSO 登录发起时缓存的 state 上下文，保存目标跳转地址和 nonce
     */
    private record SsoState(String redirect, String nonce) implements Serializable {
        @Serial
        private static final long serialVersionUID = 4056989861841419822L;
    }
}
