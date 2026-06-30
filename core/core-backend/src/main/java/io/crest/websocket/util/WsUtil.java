package io.crest.websocket.util;


import io.crest.auth.bo.TokenUserBO;
import io.crest.utils.AuthUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 在线用户状态工具
 */
public class WsUtil {

    /**
     * 当前在线用户 ID 集合
     */
    private static final CopyOnWriteArraySet<Long> ONLINE_USERS = new CopyOnWriteArraySet<>();

    /**
     * 将当前登录用户标记为在线
     */
    public static boolean onLine() {
        TokenUserBO user = AuthUtils.getUser();
        if (ObjectUtils.isNotEmpty(user) && ObjectUtils.isNotEmpty(user.getUserId()))
            return onLine(user.getUserId());
        return false;
    }

    /**
     * 将指定用户标记为在线
     */
    public static boolean onLine(Long userId) {
        return ONLINE_USERS.add(userId);
    }

    /**
     * 将当前登录用户标记为离线
     */
    public static boolean offLine() {
        TokenUserBO user = AuthUtils.getUser();
        if (ObjectUtils.isNotEmpty(user) && ObjectUtils.isNotEmpty(user.getUserId()))
            return offLine(user.getUserId());
        return false;
    }

    /**
     * 将指定用户标记为离线
     */
    public static boolean offLine(Long userId) {
        return ONLINE_USERS.remove(userId);
    }

    /**
     * 判断指定用户是否在线
     */
    public static boolean isOnLine(Long userId) {
        return ONLINE_USERS.contains(userId);
    }


}
