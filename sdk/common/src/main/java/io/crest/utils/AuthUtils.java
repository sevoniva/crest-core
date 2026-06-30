package io.crest.utils;

import io.crest.auth.bo.TokenUserBO;
import org.apache.commons.lang3.ObjectUtils;

/**
 * 当前线程认证用户上下文工具
 */
public class AuthUtils {

    /**
     * 系统管理员的固定用户 ID
     */
    private static final Long SYS_ADMIN_UID = 1L;

    /**
     * 当前线程中的登录用户信息
     */
    private static final ThreadLocal<TokenUserBO> USER_INFO = new ThreadLocal<TokenUserBO>();

    /**
     * 获取当前线程中的登录用户
     */
    public static TokenUserBO getUser() {
        if (ObjectUtils.isNotEmpty(USER_INFO.get()))
            return USER_INFO.get();
        return null;
    }

    /**
     * 设置当前线程中的登录用户
     */
    public static void setUser(TokenUserBO userBO) {
        USER_INFO.set(userBO);
    }

    /**
     * 清理当前线程中的登录用户
     */
    public static void remove() {
        USER_INFO.remove();
    }

    /**
     * 判断当前登录用户是否为系统管理员
     */
    public static boolean isSysAdmin() {
        TokenUserBO user = null;
        if (ObjectUtils.isEmpty(user = getUser())) {
            return false;
        }
        Long userId = user.getUserId();
        return isSysAdmin(userId);
    }

    /**
     * 判断指定用户 ID 是否为系统管理员
     */
    public static boolean isSysAdmin(Long userId) {
        return SYS_ADMIN_UID.equals(userId);
    }


}
