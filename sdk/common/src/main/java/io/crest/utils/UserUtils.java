package io.crest.utils;

import io.crest.auth.bo.TokenUserBO;

// 提供当前模块复用的工具能力
public class UserUtils {

    public static void setUserInfo(TokenUserBO userBO) {
        AuthUtils.setUser(userBO);
    }

    public static void setDesktopUser() {
        TokenUserBO bo = new TokenUserBO();
        bo.setUserId(1L);
        bo.setDefaultOid(1L);
        AuthUtils.setUser(bo);
    }

    public static void removeUser() {
        AuthUtils.remove();
    }
}
