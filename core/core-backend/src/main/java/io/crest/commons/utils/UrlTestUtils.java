package io.crest.commons.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

@SuppressWarnings("deprecation")
// 提供当前模块复用的工具能力
public class UrlTestUtils {

    // 格式化日期时间并返回统一展示值
    public static boolean testUrlWithTimeOut(String urlString, int timeOutMillSeconds) {
        try {
            URL url = new URL(urlString);
            URLConnection co = url.openConnection();
            co.setConnectTimeout(timeOutMillSeconds);
            co.connect();
            return true;
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            return false;
        }
    }

    // 判断当前类型是否满足业务分类
    public static boolean isURLAvailable(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }
}
