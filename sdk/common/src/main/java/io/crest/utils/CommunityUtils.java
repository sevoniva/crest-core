package io.crest.utils;

// 提供当前模块复用的工具能力
public class CommunityUtils {


    private static final ThreadLocal<String> COMMUNITY_INFO = new ThreadLocal<>();

    public static void setInfo(String info) {
        COMMUNITY_INFO.set(info);
    }

    public static String getInfo() {
        return COMMUNITY_INFO.get();
    }

    public static void removeInfo() {
        COMMUNITY_INFO.remove();
    }

}
