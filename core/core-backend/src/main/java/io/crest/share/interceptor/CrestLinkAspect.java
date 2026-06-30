package io.crest.share.interceptor;

import io.crest.auth.CrestLinkPermit;
import io.crest.constant.AuthConstant;
import io.crest.exception.CrestException;
import io.crest.share.manage.ShareLinkAccessManage;
import io.crest.utils.LogUtil;
import io.crest.utils.ServletUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 分享链接访问切面，校验带链接令牌的资源访问权限
 */
@Aspect
@Component
public class CrestLinkAspect {

    /**
     * SpEL 参数变量名前缀
     */
    private static final String PARAM_VARIABLE_PREFIX = "p";
    /**
     * SpEL 表达式起始标记
     */
    private static final String SPRING_EL_FLAG = "#";

    /**
     * SpEL 表达式解析器
     */
    private final ExpressionParser parser = new SpelExpressionParser();
    /**
     * 分享链接访问权限管理器
     */
    private final ShareLinkAccessManage shareLinkAccessManage;

    /**
     * 构造分享链接访问切面
     */
    public CrestLinkAspect(ShareLinkAccessManage shareLinkAccessManage) {
        this.shareLinkAccessManage = shareLinkAccessManage;
    }


    /**
     * 环绕拦截带分享链接许可注解的方法，并校验资源 ID 是否可访问
     */
    @Around(value = "@annotation(io.crest.auth.CrestLinkPermit)")
    public Object logAround(ProceedingJoinPoint point) throws Throwable {
        Object[] params = point.getArgs();
        String linkToken = ServletUtils.getHead(AuthConstant.LINK_TOKEN_KEY);
        if (StringUtils.isNotBlank(linkToken)) {
            MethodSignature ms = (MethodSignature) point.getSignature();
            Method method = ms.getMethod();
            CrestLinkPermit linkPermit = method.getAnnotation(CrestLinkPermit.class);
            String value = linkPermit.value();
            if (StringUtils.isBlank(value)) {
                value = SPRING_EL_FLAG + PARAM_VARIABLE_PREFIX + "0";
            }
            Long id = getExpression(params, value);
            Object resourceIdAttr = ServletUtils.request().getAttribute(AuthConstant.LINK_RESOURCE_ID_ATTR);
            if (ObjectUtils.isEmpty(resourceIdAttr)) {
                CrestException.throwException("link token invalid");
            }
            Long resourceId = Long.parseLong(resourceIdAttr.toString());
            if (!shareLinkAccessManage.canAccessWithShareResource(resourceId, id)) {
                CrestException.throwException("link token invalid");
                return false;
            }
        }
        try {
            return point.proceed(params);
        } catch (Exception e) {
            LogUtil.info(e.getMessage());
            throw e;
        }
    }

    /**
     * 从方法参数和注解表达式中解析资源 ID
     */
    public Long getExpression(Object[] params, String expression) {
        StandardEvaluationContext context = buildContext(params);
        Object o = resolveValue(expression, context);
        if (ObjectUtils.isNotEmpty(o)) return Long.parseLong(o.toString());
        return null;
    }

    /**
     * 构建 SpEL 解析上下文并注入方法参数
     */
    private StandardEvaluationContext buildContext(Object[] params) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (params != null && params.length == 1) {
            context.setRootObject(params[0]);
        }
        for (int i = 0; i < Objects.requireNonNull(params).length; i++) {
            Object paramValue = params[i];
            context.setVariable(PARAM_VARIABLE_PREFIX + i, paramValue);
        }
        return context;
    }

    // 表达式来源于内部注解配置，保留 semgrep 例外声明
    // nosemgrep: java.spring.security.audit.spel-injection.spel-injection
    private Object resolveValue(String exp, EvaluationContext context) {
        if (Strings.CS.contains(exp, SPRING_EL_FLAG)) {
            Expression expression = parser.parseExpression(exp); // nosemgrep: java.spring.security.audit.spel-injection.spel-injection
            return expression.getValue(context);
        }
        return exp;
    }
}
