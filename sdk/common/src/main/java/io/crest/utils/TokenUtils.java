package io.crest.utils;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.crest.auth.bo.TokenUserBO;
import io.crest.exception.CrestException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Token 解析与校验工具，负责从令牌中恢复当前用户身份
 */
public class TokenUtils {


    /**
     * 验证令牌签名并解析用户和组织信息
     */
    public static TokenUserBO userBOByToken(String token) {
        DecodedJWT jwt = verifiedJwt(token);
        Long userId = jwt.getClaim("uid").asLong();
        Long oid = jwt.getClaim("oid").asLong();
        if (ObjectUtils.isEmpty(userId)) {
            CrestException.throwException("token格式错误！");
        }
        return new TokenUserBO(userId, oid);
    }

    /**
     * 验证令牌签名并返回可信 JWT 内容。
     */
    public static DecodedJWT verifiedJwt(String token) {
        String secret = userSecret(token);
        return SignedTokenUtils.verify(token, secret);
    }

    /**
     * 校验业务请求令牌的基本格式并返回用户身份
     */
    public static TokenUserBO validate(String token) {
        if (StringUtils.isBlank(token)) {
            String uri = ServletUtils.request().getRequestURI();
            CrestException.throwException("token is empty for uri {" + uri + "}");
        }
        if (StringUtils.length(token) < 100) {
            CrestException.throwException("token is invalid");
        }
        return userBOByToken(token);
    }

    /**
     * 根据令牌中的用户编号查找用户专属密钥
     */
    private static String userSecret(String token) {
        try {
            DecodedJWT jwt = SignedTokenUtils.decodeUnverifiedForSecretLookup(token);
            Long uid = jwt.getClaim("uid").asLong();
            if (ObjectUtils.isEmpty(uid)) {
                CrestException.throwException("token格式错误！");
            }
            Object userManage = CommonBeanFactory.getBean("crestUserManage");
            if (userManage == null) {
                CrestException.throwException("token is invalid");
            }
            Method method = CrestReflectionUtils.findMethod(userManage.getClass(), "secretByUid");
            Object secret = ReflectionUtils.invokeMethod(method, userManage, uid);
            if (secret != null && StringUtils.isNotBlank(secret.toString())) {
                return secret.toString();
            }
        } catch (CrestException e) {
            throw e;
        } catch (Exception e) {
            CrestException.throwException("token is invalid");
        }
        CrestException.throwException("token is invalid");
        return null;
    }


    /**
     * 链接令牌必须由过滤器验证，此处仅作为禁止直接调用的保护入口
     */
    public static TokenUserBO validateLinkToken(String linkToken) {
        CrestException.throwException("link token must be verified by TokenFilter");
        return null;
    }
}
