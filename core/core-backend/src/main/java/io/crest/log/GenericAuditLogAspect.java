package io.crest.log;

import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.exception.CrestException;
import io.crest.substitute.permissions.user.CrestUserManage;
import io.crest.substitute.permissions.user.model.CrestUser;
import io.crest.utils.AuthUtils;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import io.crest.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 为未显式标注专用审计注解的 REST 接口补充通用审计日志
 */
@Aspect
@Component
public class GenericAuditLogAspect {

    /**
     * 审计日志写入服务
     */
    @Autowired
    private AuditLogService auditLogService;

    /**
     * 拦截 REST 控制器的公开方法，并在缺少专用审计注解时记录通用审计日志
     *
     * @param point 被拦截的控制器方法调用
     * @return 控制器方法的原始返回值
     * @throws Throwable 控制器方法抛出的原始异常
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *)"
            + " && execution(public * io.crest..*(..))"
            + " && !@annotation(io.crest.log.CrestAudit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (hasCrestAudit(point)) {
            return point.proceed();
        }
        HttpServletRequest request = ServletUtils.request();
        if (!shouldAudit(request, point.getSignature().getName())) {
            return point.proceed();
        }

        long startTime = System.currentTimeMillis();
        String requestMethod = request.getMethod();
        String requestUrl = request.getRequestURI();
        LogOT operationType = operationType(requestMethod, requestUrl, point.getSignature().getName());
        String resourceType = resourceType(requestUrl).name();
        Operator operator = currentOperator();
        int responseCode = 200;
        String responseMsg = "success";

        try {
            return point.proceed();
        } catch (CrestException e) {
            responseCode = e.getCode();
            responseMsg = e.getMessage();
            throw e;
        } catch (Throwable e) {
            responseCode = 500;
            responseMsg = e.getMessage();
            throw e;
        } finally {
            HttpServletResponse response = ServletUtils.response();
            if (response != null && response.getStatus() >= 400) {
                responseCode = response.getStatus();
                responseMsg = responseMsg == null || "success".equals(responseMsg) ? "failed" : responseMsg;
            }
            auditLogService.log(
                    operationType, resourceType, null, description(operationType, resourceType, requestUrl),
                    requestMethod, requestUrl,
                    operator.id(), operator.name(), operator.account(), clientIp(request),
                    System.currentTimeMillis() - startTime, responseCode, responseMsg
            );
        }
    }

    /**
     * 判断当前请求是否需要进入通用审计流程
     *
     * @param request 当前 HTTP 请求
     * @param methodName 控制器方法名称
     * @return 需要记录审计日志时返回 true
     */
    private boolean shouldAudit(HttpServletRequest request, String methodName) {
        if (request == null || request.getMethod() == null || request.getRequestURI() == null) {
            return false;
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String uri = request.getRequestURI().toLowerCase(Locale.ROOT);
        if (uri.contains("/audit-log/") || uri.contains("/auditlog/")) {
            return false;
        }
        if (hasDedicatedAudit(uri)) {
            return false;
        }
        if (isManagementReadEndpoint(uri)) {
            return true;
        }
        if (!"GET".equals(method)) {
            return true;
        }
        String normalizedName = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);
        if (containsAny(uri, "delete", "batchdel", "remove", "clear", "retry", "enable", "disable")
                || containsAny(normalizedName, "delete", "remove", "clear", "retry", "enable", "disable")) {
            return true;
        }
        return uri.contains("export") || uri.contains("download") || uri.contains("/sso/login")
                || uri.contains("/sso/callback") || uri.contains("/sso/token/");
    }

    /**
     * 判断请求路径是否已有专用审计处理，避免重复记录
     *
     * @param uri 请求路径
     * @return 已有专用审计处理时返回 true
     */
    private boolean hasDedicatedAudit(String uri) {
        return uri.contains("/sso/config")
                || uri.contains("/sso/validate")
                || uri.contains("/dataset-tree/export-dataset")
                || uri.contains("/chart-data/internal-export")
                || uri.contains("/datasettree/exportdataset")
                || uri.contains("/chartdata/innerexport");
    }

    /**
     * 判断方法声明或目标实现上是否存在专用审计注解
     *
     * @param point 被拦截的控制器方法调用
     * @return 存在专用审计注解时返回 true
     */
    private boolean hasCrestAudit(ProceedingJoinPoint point) {
        if (!(point.getSignature() instanceof MethodSignature signature)) {
            return false;
        }
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(CrestAudit.class)) {
            return true;
        }
        try {
            Method targetMethod = point.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.isAnnotationPresent(CrestAudit.class);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 根据请求方法、路径和方法名推断审计操作类型
     *
     * @param method HTTP 请求方法
     * @param uri 请求路径
     * @param methodName 控制器方法名称
     * @return 推断出的审计操作类型
     */
    private LogOT operationType(String method, String uri, String methodName) {
        String normalizedUri = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        String normalizedName = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);
        if (normalizedUri.contains("/sso/login") || normalizedUri.contains("/sso/callback")
                || normalizedUri.contains("/sso/token/")) {
            return LogOT.LOGIN;
        }
        if (normalizedUri.contains("download") || normalizedName.contains("download")) {
            return LogOT.DOWNLOAD;
        }
        if (isPermissionMutation(method, normalizedUri, normalizedName)) {
            return LogOT.AUTHORIZE;
        }
        if (isReadOperation(normalizedUri, normalizedName)) {
            return LogOT.READ;
        }
        if (normalizedUri.contains("export") || normalizedName.contains("export")) {
            return LogOT.EXPORT;
        }
        if ("DELETE".equalsIgnoreCase(method) || containsAny(normalizedUri, "delete", "batchdel", "remove")) {
            return LogOT.DELETE;
        }
        if (containsAny(normalizedUri, "authorize", "permission")
                || containsAny(normalizedName, "permission", "authorize")) {
            return LogOT.AUTHORIZE;
        }
        if (containsAny(normalizedUri, "upload", "import") || containsAny(normalizedName, "upload", "import")) {
            return LogOT.UPLOADFILE;
        }
        if ("POST".equalsIgnoreCase(method) && containsAny(normalizedName, "create", "save")) {
            return LogOT.CREATE;
        }
        if (containsAny(normalizedUri, "clear")) {
            return LogOT.CLEAR;
        }
        return LogOT.MODIFY;
    }

    /**
     * 根据请求路径推断审计资源类型
     *
     * @param uri 请求路径
     * @return 推断出的审计资源类型
     */
    private LogST resourceType(String uri) {
        String value = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (value.contains("/user/")) return LogST.USER;
        if (value.contains("/role/")) return LogST.ROLE;
        if (value.contains("/org/")) return LogST.ORG;
        if (value.contains("/auth/men")) return LogST.MENU;
        if (value.contains("/auth/")) return LogST.DATA;
        if (value.contains("/datasource-driver/") || value.contains("/datasourcedriver/")) return LogST.DRIVER;
        if (value.contains("/datasource/")) return LogST.DATASOURCE;
        if (value.contains("/dataset-sync/") || value.contains("/datasetsync/")) return LogST.SYNC_TASK;
        if (containsAny(value, "/dataset/", "/dataset-tree/", "/dataset-field/", "/dataset-data/")) return LogST.DATASET;
        if (value.contains("/data-visualization/") || value.contains("/datavisualization/")) {
            return value.contains("datav") ? LogST.SCREEN : LogST.PANEL;
        }
        if (value.contains("/chart/")) return LogST.VIEW;
        if (value.contains("/share/") || value.contains("/ticket/")) return LogST.LINK;
        if (value.contains("/sysvariable/")) return LogST.DATA;
        if (value.contains("/static-resource/") || value.contains("/staticresource/")) return LogST.DATA;
        if (value.contains("/font/")) return LogST.DATA;
        if (value.contains("/export-center/")) return LogST.DATA;
        if (value.contains("/sso/") || value.contains("/sys-parameter/") || value.contains("/sysparameter/")) return LogST.DATA;
        return LogST.DATA;
    }

    /**
     * 生成审计日志描述文本
     *
     * @param operationType 审计操作类型
     * @param resourceType 审计资源类型
     * @param requestUrl 请求路径
     * @return 审计描述文本
     */
    private String description(LogOT operationType, String resourceType, String requestUrl) {
        return AuditLogText.description(operationType, resourceType, requestUrl);
    }

    /**
     * 获取当前登录操作人的审计身份信息
     *
     * @return 当前操作人信息
     */
    private Operator currentOperator() {
        Long operatorId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
        if (operatorId == null) {
            return new Operator(null, null, null);
        }
        try {
            CrestUser user = CommonBeanFactory.getBean(CrestUserManage.class).queryById(operatorId);
            if (user != null) {
                return new Operator(operatorId, user.getName(), user.getAccount());
            }
        } catch (Exception e) {
            LogUtil.debug("获取审计操作人失败: " + e.getMessage());
        }
        return new Operator(operatorId, null, null);
    }

    /**
     * 获取用于审计记录的客户端 IP
     *
     * @param request 当前 HTTP 请求
     * @return 客户端 IP 地址
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            return index > 0 ? ip.substring(0, index).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip == null ? "unknown" : ip;
    }

    /**
     * 判断文本中是否包含任一指定片段
     *
     * @param value 待匹配文本
     * @param parts 候选片段
     * @return 命中任一片段时返回 true
     */
    private boolean containsAny(String value, String... parts) {
        for (String part : parts) {
            if (value.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断请求是否属于权限授权类变更
     *
     * @param method HTTP 请求方法
     * @param uri 请求路径
     * @param methodName 控制器方法名称
     * @return 属于权限变更时返回 true
     */
    private boolean isPermissionMutation(String method, String uri, String methodName) {
        if (containsAny(uri, "savebusiper", "savebusitargetper", "savemenuper", "savemenutargetper")
                || containsAny(methodName, "savebusiper", "savebusitargetper", "savemenuper", "savemenutargetper")) {
            return true;
        }
        return "PUT".equalsIgnoreCase(method) && containsAny(uri,
                "/auth/business-permissions",
                "/auth/business-target-permissions",
                "/auth/menu-permissions",
                "/auth/menu-target-permissions");
    }

    /**
     * 判断请求是否属于需要审计的管理类读取接口
     *
     * @param uri 请求路径
     * @return 属于管理类读取接口时返回 true
     */
    private boolean isManagementReadEndpoint(String uri) {
        return containsAny(uri,
                "/sys-parameter/", "/sysparameter/", "/engine/", "/export-center/export-tasks", "/export-center/export-limit",
                "/auth/business-permissions", "/auth/business-target-permissions", "/auth/business-resources",
                "/auth/menu-permissions", "/auth/menu-target-permissions", "/auth/menu-resources",
                "/auth/busipermission", "/auth/busitargetpermission", "/auth/busiresource",
                "/auth/menupermission", "/auth/menutargetpermission", "/auth/menuresource",
                "/role/by-current-org", "/role/bycurorg", "/role/list", "/role/detail", "/role/listwithoid",
                "/user/by-current-org", "/user/bycurorg", "/user/default-password", "/user/defaultpwd", "/org/mounted");
    }

    /**
     * 判断请求是否属于普通读取操作
     *
     * @param uri 请求路径
     * @param methodName 控制器方法名称
     * @return 属于读取操作时返回 true
     */
    private boolean isReadOperation(String uri, String methodName) {
        return containsAny(uri,
                "/list", "/pager", "/tree", "/info", "/detail", "/status", "/option", "/selected",
                "/by-current-org", "/bycurorg", "/listbyid", "/listwithoid", "/mounted", "/permission", "/resource",
                "/export-tasks", "/export-limit", "/validatepwd")
                || containsAny(methodName, "query", "pager", "tree", "info", "detail", "status", "option",
                "selected", "permission", "resource", "validate");
    }

    /**
     * 审计日志中的操作人快照
     *
     * @param id 操作人 ID
     * @param name 操作人姓名
     * @param account 操作人账号
     */
    private record Operator(Long id, String name, String account) {
    }
}
