package io.crest.utils;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 客户端请求超时和请求头配置
 */
public class HttpClientConfig {

    // 默认请求字符集
    private String charset = "UTF-8";

    // 请求头键值集合
    private Map<String, String> header = new HashMap<>();

    // 建立连接的超时时间，单位毫秒
    private int connectTimeout = 30000;
    // 从连接管理器获取连接的超时时间，单位毫秒
    private int connectionRequestTimeout = 30000;
    // 等待接口返回数据的超时时间，单位毫秒
    private int socketTimeout = 60000;

    /**
     * 构造 Apache HttpClient 使用的请求配置
     */
    public RequestConfig buildRequestConfig() {
        Builder builder = RequestConfig.custom();
        builder.setConnectTimeout(connectTimeout);
        builder.setConnectionRequestTimeout(connectionRequestTimeout);
        builder.setSocketTimeout(socketTimeout);
        return builder.build();
    }

    /**
     * 获取请求字符集
     */
    public String getCharset() {
        return charset;
    }

    /**
     * 设置请求字符集
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * 获取请求头集合
     */
    public Map<String, String> getHeader() {
        return header;
    }

    /**
     * 添加单个请求头
     */
    public void addHeader(String key, String value) {
        header.put(key, value);
    }

    /**
     * 获取建立连接超时时间
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 设置建立连接超时时间
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * 获取连接池取连接超时时间
     */
    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /**
     * 设置连接池取连接超时时间
     */
    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    /**
     * 获取数据读取超时时间
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * 设置数据读取超时时间
     */
    public void setSocketTimeout(int cocketTimeout) {
        this.socketTimeout = cocketTimeout;
    }

}
