package io.crest.utils;

import io.crest.constant.AuthConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 提供当前请求上下文中的 Servlet 对象和认证头读取能力
 */
public class ServletUtils {

    /**
     * 获取当前线程绑定的 HTTP 请求对象
     */
    public static HttpServletRequest request() {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (ObjectUtils.isEmpty(servletRequestAttributes)) return null;
        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request;
    }

    /**
     * 获取当前线程绑定的 HTTP 响应对象
     */
    public static HttpServletResponse response() {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (ObjectUtils.isEmpty(servletRequestAttributes)) return null;
        HttpServletResponse response = servletRequestAttributes.getResponse();
        return response;
    }

    /**
     * 读取当前请求中的指定请求头
     */
    public static String getHead(String key) {
        HttpServletRequest request = request();
        return request.getHeader(key);
    }

    /**
     * 获取普通访问令牌，缺失时回退到嵌入式访问令牌
     */
    public static String getToken() {
        String token = getHead(AuthConstant.TOKEN_KEY);
        return StringUtils.isNotBlank(token) ? token : getEmbeddedToken();
    }

    /**
     * 获取嵌入式访问令牌并兼容旧版请求头
     */
    public static String getEmbeddedToken() {
        String token = getHead(AuthConstant.EMBEDDED_TOKEN_KEY);
        return StringUtils.isNotBlank(token) ? token : getHead(AuthConstant.LEGACY_EMBEDDED_TOKEN_KEY);
    }

    /**
     * 获取 OIDC 透传的用户信息请求头
     */
    public static String getXUserinfo() {
        return getHead(AuthConstant.OIDC_X_USER);
    }

    /**
     * 获取 LDAP 认证透传的用户标识
     */
    public static String getLdapUser() {
        String authorization = getHead(AuthConstant.CREST_LDAP_AUTHORIZATION);
        if (StringUtils.isBlank(authorization)) return null;
        return authorization;
    }

    /**
     * 获取 CAS 认证透传的用户标识
     */
    public static String getCasUser() {
        return getHead(AuthConstant.CAS_X_USER);
    }

    /**
     * 保留网关检查入口，当前默认放行
     */
    public static boolean apisixCheck() {
        return true;
    }


}
