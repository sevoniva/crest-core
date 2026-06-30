package io.crest.system.sso;

import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.URI;
import java.util.Objects;

// 定义单点登录相关的业务模型和处理入口
public final class SsoEndpointPolicy {

    private SsoEndpointPolicy() {
    }

    // 格式化日期时间并返回统一展示值
    public static String validateEndpoint(String endpoint, Boolean requireHttps, String name) {
        if (StringUtils.isBlank(endpoint)) {
            CrestException.throwException(name + "不能为空");
        }
        String value = endpoint.trim();
        if (!Objects.equals(value, endpoint)) {
            // Trimmed endpoints are accepted because many IdP admin consoles copy a trailing space.
            endpoint = value;
        }
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!Strings.CI.equalsAny(scheme, "https", "http") || StringUtils.isBlank(host)) {
                CrestException.throwException(name + "必须是 HTTP 或 HTTPS 绝对地址");
            }
            if (StringUtils.isNotBlank(uri.getUserInfo())) {
                CrestException.throwException(name + "不能包含用户名或密码");
            }
            if (endpoint.chars().anyMatch(Character::isISOControl)) {
                CrestException.throwException(name + "不能包含控制字符");
            }
            if (Boolean.TRUE.equals(requireHttps)
                    && !Strings.CI.equals(scheme, "https")
                    && !isLocalHost(host)) {
                CrestException.throwException(name + "生产环境必须使用 HTTPS");
            }
            return endpoint;
        } catch (IllegalArgumentException e) {
            CrestException.throwException(name + "格式无效");
            return null;
        }
    }

    // 判断当前类型是否满足业务分类
    private static boolean isLocalHost(String host) {
        String normalized = host;
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return Strings.CI.equalsAny(normalized, "localhost", "127.0.0.1", "::1");
    }
}
