package io.crest.utils;

import io.crest.auth.bo.TokenUserBO;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.metadata.MetadataDbType;
import io.crest.result.ResultCode;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * 社区版当前用户权限判断的共享工具方法
 */
public class CrestPermissionUtils {

    /**
     * 判断当前用户是否拥有管理员权限
     */
    public static boolean currentUserIsAdmin() {
        Long uid = currentUserId();
        if (ObjectUtils.isEmpty(uid)) {
            return false;
        }
        try {
            Object userManage = CommonBeanFactory.getBean("crestUserManage");
            Method method = CrestReflectionUtils.findMethod(userManage.getClass(), "isAdmin");
            Object result = ReflectionUtils.invokeMethod(method, userManage, uid);
            if (result instanceof Boolean bool) {
                return bool;
            }
        } catch (Exception ignored) {
        }
        return uid == 1L;
    }

    /**
     * 当前用户不是管理员时抛出权限异常
     */
    public static void requireAdmin() {
        if (!currentUserIsAdmin()) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户没有管理员权限");
        }
    }

    /**
     * 判断管理员或资源创建人是否可访问创建人范围资源
     */
    public static boolean canAccessCreator(String createBy) {
        if (currentUserIsAdmin()) {
            return true;
        }
        Long uid = currentUserId();
        return uid != null && Strings.CS.equals(String.valueOf(uid), StringUtils.trimToEmpty(createBy));
    }

    /**
     * 当前用户不能访问创建人范围资源时抛出权限异常
     */
    public static void requireCreator(String createBy) {
        if (currentUserId() == null) {
            return;
        }
        if (!canAccessCreator(createBy)) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户无权访问该资源");
        }
    }

    /**
     * 从令牌上下文读取当前认证用户 ID
     */
    public static Long currentUserId() {
        TokenUserBO user = AuthUtils.getUser();
        return user == null ? null : user.getUserId();
    }

    /**
     * 构建用于隐藏其他用户资源的 SQL 过滤片段
     */
    public static String communityScopeSql() {
        Long uid = currentUserId();
        if (ObjectUtils.isEmpty(uid) || currentUserIsAdmin()) {
            return null;
        }
        String safeUid = Strings.CS.replace(String.valueOf(uid), "'", "''");
        MetadataDbDialect dialect = metadataDialect();
        String idExpression = dialect.stringCast("id");
        String targetIdExpression = dialect.stringCast("%s");
        return "SELECT 1 FROM (\n"
                + "  SELECT " + idExpression + " AS resource_id, create_by FROM core_datasource\n"
                + "  UNION ALL SELECT " + idExpression + " AS resource_id, create_by FROM core_dataset\n"
                + "  UNION ALL SELECT " + idExpression + " AS resource_id, create_by FROM core_chart_view\n"
                + "  UNION ALL SELECT " + idExpression + " AS resource_id, create_by FROM core_visualization\n"
                + ") resource_scope\n"
                + "WHERE resource_scope.resource_id = " + targetIdExpression + "\n"
                + "  AND (resource_scope.create_by IS NULL OR resource_scope.create_by <> '" + safeUid + "')";
    }

    /**
     * 静态工具可能在非 Spring 场景被调用，无法解析环境时使用 OB Oracle 默认行为。
     */
    private static MetadataDbDialect metadataDialect() {
        Environment environment = CommonBeanFactory.getBean(Environment.class);
        if (environment == null) {
            return MetadataDbDialects.forType(MetadataDbType.OB_ORACLE);
        }
        return MetadataDbDialects.current(environment);
    }
}
