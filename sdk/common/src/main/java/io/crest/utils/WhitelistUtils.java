package io.crest.utils;

import io.crest.constant.AuthConstant;
import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.core.env.Environment;

import java.util.List;

import static io.crest.result.ResultCode.INTERFACE_ADDRESS_INVALID;

/**
 * 匿名访问白名单工具，负责标准化请求路径并判断是否跳过鉴权
 */
public class WhitelistUtils {

    /**
     * 服务上下文路径缓存，首次读取后复用环境配置
     */
    private static String contextPath;


    /**
     * 获取服务上下文路径，环境未配置时返回空字符串
     */
    public static String getContextPath() {
        if (StringUtils.isBlank(contextPath)) {
            Environment environment = CommonBeanFactory.getBean(Environment.class);
            contextPath = environment == null ? "" : environment.getProperty("server.servlet.context-path", String.class);
        }
        return contextPath;
    }

    /**
     * 固定白名单路径，主要覆盖登录、健康检查、公开配置和静态入口
     */
    public static List<String> WHITE_PATH = List.of(
            "/login/local-login",
            "/apisix/check",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/public-key",
            "/index.html",
            "/model",
            "/panel.html",
            "/mobile.html",
            "/sys-parameter/request-timeout",
            "/sys-parameter/default-settings",
            "/setting/authentication/status",
            "/sys-parameter/ui",
            "/sys-parameter/default-login",
            "/sso/public/status",
            "/sso/login",
            "/sso/callback",
            "/embedded/initIframe",
            "/sys-parameter/i18n-options",
            "/login/modify-invalid-password",
            "/share/proxy-info",
            "/share/validate",
            "/perSetting/hmac/info",
            "/");

    /**
     * 判断请求路径是否命中白名单或静态资源规则
     */
    public static boolean match(String requestURI) {
        invalidUrl(requestURI);
        if (Strings.CS.startsWith(requestURI, getContextPath())) {
            requestURI = requestURI.replaceFirst(getContextPath(), "");
        }
        if (Strings.CS.startsWith(requestURI, AuthConstant.CREST_API_PREFIX)) {
            requestURI = requestURI.replaceFirst(AuthConstant.CREST_API_PREFIX, "");
        }
        if (Strings.CS.startsWith(requestURI, AuthConstant.CREST_CASAPI_PREFIX)) {
            requestURI = requestURI.replaceFirst(AuthConstant.CREST_CASAPI_PREFIX, "");
        }
        if (Strings.CS.startsWith(requestURI, AuthConstant.CREST_OIDCAPI_PREFIX)) {
            requestURI = requestURI.replaceFirst(AuthConstant.CREST_OIDCAPI_PREFIX, "");
        }
        return WHITE_PATH.contains(requestURI)
                || Strings.CS.endsWithAny(requestURI, ".gif", ".ico", ".js", ".css", ".svg", ".png", ".jpg", ".jpeg", ".js.map", ".otf", ".ttf", ".woff2")
                || Strings.CS.startsWithAny(requestURI, "data:image")
                || Strings.CS.startsWithAny(requestURI, "/login/platform-login/")
                // 移除API文档白名单，需要认证才能访问
                // || Strings.CS.startsWithAny(requestURI, "/v3/api-docs")
                // || Strings.CS.startsWithAny(requestURI, "/swagger-ui")
                || Strings.CS.startsWithAny(requestURI, "/static-resource/")
                || Strings.CS.startsWithAny(requestURI, "/appearance/image/")
                || Strings.CS.startsWithAny(requestURI, "/websocket")
                || Strings.CS.startsWithAny(requestURI, "/sso/token/")
                || Strings.CS.startsWithAny(requestURI, "/mfa/qr/")
                || Strings.CS.startsWithAny(requestURI, "/mfa/login")
                || Strings.CS.startsWithAny(requestURI, "/typeface/download")
                || Strings.CS.startsWithAny(requestURI, "/typeface/default")
                || Strings.CS.startsWithAny(requestURI, "/typeface/fonts")
                || Strings.CS.startsWithAny(requestURI, "/export-center/download/")
                || Strings.CS.startsWithAny(requestURI, "/i18n/")
                || Strings.CS.startsWithAny(requestURI, "/communicate/image/")
                || Strings.CS.startsWithAny(requestURI, "/communicate/down/");
    }

    /**
     * 根据回调地址拼接后端基础接口地址
     */
    public static String getBaseApiUrl(String redirect_uri) {
        if (Strings.CS.endsWith(redirect_uri, "/")) {
            redirect_uri = redirect_uri.substring(0, redirect_uri.length() - 1);
        }
        String contextPath = WhitelistUtils.getContextPath();
        if (StringUtils.isNotBlank(contextPath)) {
            redirect_uri += contextPath;
        }
        return redirect_uri + AuthConstant.CREST_API_PREFIX + "/";
    }

    /**
     * 拦截包含路径穿越或异常分号的非法接口地址
     */
    private static void invalidUrl(String requestURI) {
        if (requestURI.contains("./") || requestURI.contains(".%") || requestURI.toLowerCase().contains("%2e") || (requestURI.contains(";") && !requestURI.contains("?"))) {
            CrestException.throwException(INTERFACE_ADDRESS_INVALID.code(), String.format("%s [%s]", INTERFACE_ADDRESS_INVALID.message(), requestURI));
        }
    }
}
