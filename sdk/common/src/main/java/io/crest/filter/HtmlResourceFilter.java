package io.crest.filter;

import jakarta.servlet.*;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为前端静态资源应用缓存和安全响应头策略
 */
@Component
public class HtmlResourceFilter implements Filter, Ordered {

    private static final Pattern VERSIONED_FRONTEND_ASSET = Pattern.compile(
            "^(js|assets/(?:chunk|css|svg|png|jpg|jpeg|gif|ico|woff2?))/(.+)-\\d+\\.\\d+\\.\\d+-(crest.*)$"
    );
    private static final String FRONTEND_CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: http: https:",
            "font-src 'self' data:",
            "connect-src 'self' http: https: ws: wss:",
            "media-src 'self' data: blob: http: https:",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-src 'self' http: https:",
            "frame-ancestors 'self'",
            "form-action 'self'",
            "worker-src 'self' blob:");

    @Value("${crest.http.cache:false}")
    private Boolean httpCache;

    @Value("${crest.version:}")
    private String configuredVersion;

    /**
     * 设置过滤器执行顺序
     */
    @Override
    public int getOrder() {
        return 99;
    }

    /**
     * 初始化过滤器配置
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /**
     * 处理前端资源版本兼容转发并应用响应头
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest request
                && servletResponse instanceof HttpServletResponse response
                && forwardStaleFrontendAsset(request, response)) {
            return;
        }

        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        applyNoCache(httpResponse);
        // 继续执行过滤器链
        filterChain.doFilter(servletRequest, httpResponse);
    }

    /**
     * 将带旧版本号的前端资源转发到当前版本资源
     */
    private boolean forwardStaleFrontendAsset(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }

        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        requestPath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        Matcher matcher = VERSIONED_FRONTEND_ASSET.matcher(requestPath);
        if (!matcher.matches()) {
            return false;
        }

        if (new ClassPathResource("static/" + requestPath).exists()) {
            return false;
        }

        String currentVersion = currentVersion();
        if (currentVersion == null || currentVersion.isBlank()) {
            return false;
        }

        String currentPath = matcher.group(1) + "/" + matcher.group(2) + "-" + currentVersion + "-" + matcher.group(3);
        if (!new ClassPathResource("static/" + currentPath).exists()) {
            return false;
        }

        applyNoCache(response);
        request.getRequestDispatcher("/" + currentPath).forward(request, response);
        return true;
    }

    /**
     * 获取当前前端资源版本号
     */
    private String currentVersion() {
        if (configuredVersion != null && !configuredVersion.isBlank()) {
            return configuredVersion.trim();
        }
        Package pkg = HtmlResourceFilter.class.getPackage();
        return pkg == null ? null : pkg.getImplementationVersion();
    }

    /**
     * 为前端资源响应设置禁用缓存和安全响应头
     */
    private void applyNoCache(HttpServletResponse response) {
        applySecurityHeaders(response);
        if (httpCache == null || !httpCache) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");
        }
    }

    /**
     * 设置前端页面所需的基础安全响应头
     */
    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Content-Security-Policy", FRONTEND_CONTENT_SECURITY_POLICY);
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
    }

    /**
     * 销毁过滤器并执行默认清理逻辑
     */
    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
