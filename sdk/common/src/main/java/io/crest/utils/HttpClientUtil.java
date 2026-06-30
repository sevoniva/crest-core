package io.crest.utils;

import io.crest.exception.CrestException;
import lombok.Data;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.crest.result.ResultCode.SYSTEM_INNER_ERROR;

/**
 * HTTP 客户端工具，封装通用请求、下载、上传和响应解析能力
 */
@SuppressWarnings({"deprecation", "unchecked"})
public class HttpClientUtil {

    /**
     * 统一记录 HTTP 请求和连接关闭异常
     */
    private static Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

    /**
     * 下载文件内容写入器，由调用方决定目标存储介质。
     */
    @FunctionalInterface
    public interface DownloadFileWriter {
        void write(String tranName, InputStream inputStream) throws IOException;
    }

    /**
     * HTTPS 协议前缀，用于选择 TLS 客户端构造路径
     */
    private static final String HTTPS = "https";

    /**
     * 根据url构建HttpClient（区分http和https）
     *
     * @param url 请求地址
     * @return CloseableHttpClient实例
     */
    private static CloseableHttpClient buildHttpClient(String url) {
        if (StringUtils.isEmpty(url)) {
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: url 不能为空！");
        }
        try {
            if (url.startsWith(HTTPS)) {
                return buildHttpClient(true);
            } else {
                // http
                return HttpClientBuilder.create().build();
            }
        } catch (Exception e) {
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        }
    }

    /**
     * 按是否启用 TLS 构建底层 HTTP 客户端
     */
    private static CloseableHttpClient buildHttpClient(boolean ssl) {
        try {
            if (ssl) {
                SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                        SSLContexts.createDefault(),
                        new String[]{"TLSv1.2", "TLSv1.3"},
                        null,
                        SSLConnectionSocketFactory.getDefaultHostnameVerifier()
                );
                Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", new PlainConnectionSocketFactory())
                        .register("https", socketFactory).build();
                HttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(registry);
                return HttpClients.custom().setConnectionManager(connManager).build();
            } else {
                // http
                return HttpClientBuilder.create().build();
            }
        } catch (Exception e) {
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        }
    }

    /**
     * 校验目标地址是否可访问，并复用请求配置中的超时和请求头
     */
    public static boolean validateUrl(String url, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpGet httpGet = new HttpGet(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpGet.setConfig(config.buildRequestConfig());

            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpGet.addHeader(key, header.get(key));
            }
            HttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() >= 400) {
                String msg = EntityUtils.toString(response.getEntity(), config.getCharset());
                if (StringUtils.isEmpty(msg)) {
                    msg = "StatusCode: " + response.getStatusLine().getStatusCode();
                }
                throw new Exception(msg);
            }
            return true;
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * Get http请求
     *
     * @param url    请求地址
     * @param config 配置项，如果null则使用默认配置
     * @return 响应结果字符串
     */
    public static String get(String url, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpGet httpGet = new HttpGet(url);

            if (config == null) {
                config = new HttpClientConfig();
            }
            httpGet.setConfig(config.buildRequestConfig());

            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpGet.addHeader(key, header.get(key));
            }
            HttpResponse response = httpClient.execute(httpGet);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * 发送 JSON PATCH 请求并返回响应文本
     */
    public static String patch(String url, String json, HttpClientConfig config) {
        CloseableHttpClient httpClient = buildHttpClient(url);
        HttpPatch httpPatch = new HttpPatch(url);
        config = config == null ? new HttpClientConfig() : config;
        try {
            httpPatch.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPatch.addHeader(key, header.get(key));
            }
            EntityBuilder entityBuilder = EntityBuilder.create();
            entityBuilder.setText(json);
            entityBuilder.setContentType(ContentType.APPLICATION_JSON);
            HttpEntity requestEntity = entityBuilder.build();
            httpPatch.setEntity(requestEntity);
            HttpResponse response = httpClient.execute(httpPatch);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * Post请求，请求内容必须为JSON格式的字符串
     *
     * @param url    请求地址
     * @param config 配置项，如果null则使用默认配置
     * @param json   JSON格式的字符串
     * @return 响应结果字符串
     */
    public static String post(String url, String json, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpPost httpPost = new HttpPost(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpPost.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPost.addHeader(key, header.get(key));
            }
            EntityBuilder entityBuilder = EntityBuilder.create();
            entityBuilder.setText(json);
            entityBuilder.setContentType(ContentType.APPLICATION_JSON);
            HttpEntity requestEntity = entityBuilder.build();
            httpPost.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(httpPost);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * 发送 JSON POST 请求并保留完整响应头和状态
     */
    public static HttpResponse postWithHeaders(String url, String json, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpPost httpPost = new HttpPost(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpPost.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPost.addHeader(key, header.get(key));
            }
            EntityBuilder entityBuilder = EntityBuilder.create();
            entityBuilder.setText(json);
            entityBuilder.setContentType(ContentType.APPLICATION_JSON);
            HttpEntity requestEntity = entityBuilder.build();
            httpPost.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(httpPost);
            return response;
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * 发送 JSON PUT 请求并返回响应文本
     */
    public static String put(String url, String json, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpPut httpPut = new HttpPut(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpPut.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPut.addHeader(key, header.get(key));
            }
            EntityBuilder entityBuilder = EntityBuilder.create();
            entityBuilder.setText(json);
            entityBuilder.setContentType(ContentType.APPLICATION_JSON);
            HttpEntity requestEntity = entityBuilder.build();
            httpPut.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(httpPut);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * Post请求，请求内容必须为JSON格式的字符串
     *
     * @param url  请求地址
     * @param json JSON格式的字符串
     * @return 响应结果字符串
     */
    public static String post(String url, String json) {
        return HttpClientUtil.post(url, json, null);
    }

    /**
     * Post请求，请求内容必须为键值对参数
     *
     * @param url    请求地址
     * @param config 配置项，如果null则使用默认配置
     * @param body   请求内容键值对参数
     * @return 响应结果字符串
     */
    public static String post(String url, Map<String, String> body, HttpClientConfig config) {
        try (CloseableHttpClient httpClient = buildHttpClient(url)) {
            HttpPost httpPost = new HttpPost(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpPost.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPost.addHeader(key, header.get(key));
            }
            if (body != null && body.size() > 0) {
                List<NameValuePair> nvps = new ArrayList<>();
                for (String key : body.keySet()) {
                    nvps.add(new BasicNameValuePair(key, body.get(key)));
                }
                try {
                    httpPost.setEntity(new UrlEncodedFormEntity(nvps, config.getCharset()));
                } catch (Exception e) {
                    logger.error("HttpClient转换编码错误", e);
                    throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient转换编码错误: " + e.getMessage());
                }
            }

            HttpResponse response = httpClient.execute(httpPost);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        }
    }

    /**
     * 将 HTTP 响应转换为字符串，并把错误状态码转换成异常
     */
    private static String getResponseStr(HttpResponse response, HttpClientConfig config) throws Exception {
        if (response.getStatusLine().getStatusCode() >= 400) {
            String msg = EntityUtils.toString(response.getEntity(), config.getCharset());
            if (StringUtils.isEmpty(msg)) {
                msg = "StatusCode: " + response.getStatusLine().getStatusCode();
            }
            throw new Exception(msg);
        }
        return EntityUtils.toString(response.getEntity(), config.getCharset());
    }

    /**
     * 下载远程文件到指定目录，并返回原始文件名和本地转换文件名
     */
    public static Map<String, String> downloadFile(String url, HttpClientConfig config, String path) {
        return downloadFile(url, config, (tranName, inputStream) -> {
            File localFile = FileUtils.resolveUnderDirectory(path, tranName);
            try (FileOutputStream outputStream = new FileOutputStream(localFile)) {
                copy(inputStream, outputStream);
            }
        });
    }

    /**
     * 下载远程文件并由调用方写入目标存储，避免公共模块绑定具体文件系统。
     */
    public static Map<String, String> downloadFile(String url, HttpClientConfig config, DownloadFileWriter writer) {
        validateRemoteDownloadUrl(url);
        String encodeUIl = url;
        Map<String, String> name = new HashMap<>();
        if (!url.contains("%")) {
            String[] http = url.split("://");
            String[] server = http[1].split("/");
            encodeUIl = http[0] + "://" + server[0] + "/" + URLEncoder.encode(http[1].substring(server[0].length() + 1, http[1].length()));
        }
        try (CloseableHttpClient httpClient = buildHttpClient(encodeUIl.replace("+", "%20"))) {
            HttpGet httpGet = new HttpGet(encodeUIl.replace("+", "%20"));
            // 设置请求配置
            httpGet.setConfig(config.buildRequestConfig());
            // 设置请求头
            config.getHeader().forEach(httpGet::addHeader);
            HttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() >= 400) {
                String msg = EntityUtils.toString(response.getEntity(), config.getCharset());
                if (StringUtils.isEmpty(msg)) {
                    msg = "StatusCode: " + response.getStatusLine().getStatusCode();
                }
                throw new Exception(msg);
            }
            String fileName = extractFileName(response, url);
            String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
            String tranName = UUID.randomUUID().toString() + "." + suffix;
            name.put("fileName", fileName);
            name.put("tranName", tranName);
            try (InputStream is = response.getEntity().getContent()) {
                writer.write(tranName, is);
            }
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new RuntimeException("HttpClient查询失败: " + e.getMessage(), e);
        }
        return name;
    }

    /**
     * 流式复制下载内容，避免大文件一次性载入内存。
     */
    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    /**
     * 校验远程下载地址协议和主机范围，阻断本地或私有地址访问
     */
    public static void validateRemoteDownloadUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!Strings.CI.equalsAny(scheme, "http", "https")) {
                throw new IllegalArgumentException("only http and https downloads are allowed");
            }
            assertRemoteHostAllowed(uri.getHost());
        } catch (Exception e) {
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "远程下载地址不合法: " + e.getMessage());
        }
    }

    /**
     * 解析主机地址并校验是否允许远程下载访问
     */
    public static void assertRemoteHostAllowed(String host) throws UnknownHostException {
        if (StringUtils.isBlank(host) || host.contains("/") || host.contains("\\") || host.contains("@")) {
            throw new UnknownHostException("invalid host");
        }
        String lookupHost = normalizeLookupHost(host);
        if (StringUtils.isBlank(lookupHost)) {
            throw new UnknownHostException("invalid host");
        }
        if (Boolean.parseBoolean(ConfigUtils.getConfig("crest.security.remote-download.allow-private-address", "false"))) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(lookupHost)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new UnknownHostException("private or local address is not allowed");
            }
        }
    }

    /**
     * 规范化主机名，去除 IPv6 方括号或 URL 中携带的端口
     */
    private static String normalizeLookupHost(String host) {
        String value = host.trim();
        if (value.startsWith("[") && value.contains("]")) {
            return value.substring(1, value.indexOf(']'));
        }
        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon == lastColon && firstColon > 0) {
            return value.substring(0, firstColon);
        }
        return value;
    }

    /**
     * 优先从响应头提取文件名，缺失时回退到下载地址路径
     */
    private static String extractFileName(HttpResponse response, String url) {
        url = URLDecoder.decode(url);
        String fileName = "";
        String disposition = response.getHeaders("Content-Disposition").toString();
        if (disposition != null) {
            int filenameIndex = disposition.indexOf("filename=");
            if (filenameIndex > 0) {
                fileName = disposition.substring(filenameIndex + 9)
                        .replaceAll("\"", "") // 去除引号
                        .trim();
            }
        }
        if (fileName.isEmpty()) {
            url = url.split("\\?")[0];
            fileName = url.contains("/")
                    ? url.substring(url.lastIndexOf('/') + 1)
                    : "download_" + System.currentTimeMillis();
        }
        if (fileName.trim().isEmpty()) {
            fileName = "download_" + System.currentTimeMillis();
        }
        return fileName;
    }

    /**
     * 使用默认配置下载远程资源为字节数组
     */
    public static byte[] downloadBytes(String url) {
        HttpClientConfig config = new HttpClientConfig();
        return HttpClientUtil.downFromRemote(url, config);
    }

    /**
     * 按指定请求配置下载远程资源为字节数组
     */
    public static byte[] downFromRemote(String url, HttpClientConfig config) {
        validateRemoteDownloadUrl(url);
        try (CloseableHttpClient httpClient = buildHttpClient(url)) {
            HttpGet httpGet = new HttpGet(url);
            // 设置请求配置
            httpGet.setConfig(config.buildRequestConfig());

            // 设置请求头
            config.getHeader().forEach(httpGet::addHeader);
            HttpResponse response = httpClient.execute(httpGet);
            try (InputStream inputStream = response.getEntity().getContent();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int bytesRead;

                // 读取响应内容并写入输出流
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new RuntimeException("HttpClient查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 以 multipart 形式上传字节数组文件和附加表单参数
     */
    public static String postFile(String fileServer, byte[] bytes, String fileName, Map<String, String> param, HttpClientConfig config) {
        CloseableHttpClient httpClient = buildHttpClient(fileServer);
        HttpPost postRequest = new HttpPost(fileServer);
        if (config == null) {
            config = new HttpClientConfig();
        }

        postRequest.setConfig(config.buildRequestConfig());
        Map<String, String> header = config.getHeader();
        if (MapUtils.isNotEmpty(header)) {
            Iterator var8 = header.keySet().iterator();

            while (var8.hasNext()) {
                String key = (String) var8.next();
                postRequest.addHeader(key, (String) header.get(key));
            }
        }

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setCharset(StandardCharsets.UTF_8);
        builder.addBinaryBody("image", bytes, ContentType.DEFAULT_BINARY, fileName);
        if (param != null) {
            Iterator var13 = param.entrySet().iterator();
            while (var13.hasNext()) {
                Map.Entry<String, String> entry = (Map.Entry) var13.next();
                builder.addTextBody((String) entry.getKey(), (String) entry.getValue());
            }
        }
        try {
            postRequest.setEntity((HttpEntity) builder.build());
            return getResponseStr(httpClient.execute(postRequest), config);
        } catch (Exception var11) {
            logger.error("HttpClient查询失败", var11);
            throw new RuntimeException("HttpClient查询失败: " + var11.getMessage());
        }
    }

    /**
     * 上传内存中的文件内容，并把对象型请求头转换为字符串请求头
     */
    public static String upload(String url, byte[] bytes, String name, Map<String, String> paramMap, Map<String, Object> headMap) {
        HttpClientConfig config = new HttpClientConfig();
        addHead(config, headMap);
        return HttpClientUtil.postFile(url, bytes, name, paramMap, config);
    }

    /**
     * 上传本地文件并携带媒体文件标识
     */
    public static String upload(String url, File file, String name) {
        HttpClientConfig config = new HttpClientConfig();
        Map<String, String> param = new HashMap<>();
        param.put("fileFlag", "media");
        param.put("fileName", name);
        return HttpClientUtil.postFile(url, file, param, config);
    }

    /**
     * 以 multipart 形式上传本地文件和附加表单参数
     */
    public static String postFile(String fileServer, File file, Map<String, String> param, HttpClientConfig config) {
        CloseableHttpClient httpClient = buildHttpClient(fileServer);
        HttpPost postRequest = new HttpPost(fileServer);
        if (config == null) {
            config = new HttpClientConfig();
        }
        postRequest.setConfig(config.buildRequestConfig());
        Map<String, String> header = config.getHeader();
        String fileFlag = param.get("fileFlag");
        String fileName = param.get("fileName");
        param.remove("fileFlag");
        param.remove("fileName");
        if (MapUtils.isNotEmpty(header)) {
            for (String key : header.keySet()) {
                postRequest.addHeader(key, header.get(key));
            }
        }
        postRequest.setHeader("Content-Type", "multipart/form-data");
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setCharset(StandardCharsets.UTF_8);
        builder.addBinaryBody(StringUtils.isNotBlank(fileFlag) ? fileFlag : "file", file, ContentType.APPLICATION_OCTET_STREAM, StringUtils.isNotBlank(fileName) ? fileName : file.getName());
        if (MapUtils.isNotEmpty(param)) {
            for (Map.Entry<String, String> entry : param.entrySet()) {
                StringBody stringBody = new StringBody(entry.getValue(), ContentType.TEXT_PLAIN.withCharset("utf-8"));
                builder.addPart(entry.getKey(), stringBody);
            }
        }
        try {
            postRequest.setEntity(builder.build());
            return getResponseStr(httpClient.execute(postRequest), config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new RuntimeException("HttpClient查询失败: " + e.getMessage());
        }
    }

    /**
     * 将外部传入的请求头对象写入 HTTP 配置
     */
    private static void addHead(HttpClientConfig config, Map<String, Object> headMap) {
        if (MapUtils.isEmpty(headMap)) return;
        for (Map.Entry<String, Object> entry : headMap.entrySet()) {
            config.addHeader(entry.getKey(), entry.getValue().toString());
        }
    }

    /**
     * 发送 DELETE 请求并返回响应文本
     */
    public static String delete(String url, HttpClientConfig config) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpDelete httpDelete = new HttpDelete(url);

            if (config == null) {
                config = new HttpClientConfig();
            }
            httpDelete.setConfig(config.buildRequestConfig());

            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpDelete.addHeader(key, header.get(key));
            }
            HttpResponse response = httpClient.execute(httpDelete);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * 使用轻量 GET 请求检测目标地址是否可达
     */
    public static boolean isURLReachable(String urlString, Map<String, String> head) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 设置连接超时时间，单位为毫秒
            connection.setReadTimeout(5000); // 设置读取超时时间，单位为毫秒
            if (MapUtils.isNotEmpty(head)) {
                for (Map.Entry<String, String> entry : head.entrySet()) {
                    connection.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true; // 状态码200表示URL可达
            } else if (Strings.CI.equals("Unauthorized", connection.getResponseMessage())) {
                LogUtil.error("apisix key error [failed to check token]");
            }
        } catch (IOException e) {
            return false;
        }
        return false; // 如果发生异常或状态码不是200，则URL不可达
    }

    /**
     * 按内容类型发送 webhook 请求，支持 JSON 和表单编码
     */
    public static String postWebhook(String url, String contentType, Map<String, Object> param, boolean ssl, HttpClientConfig config) {

        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(ssl);
            HttpPost httpPost = new HttpPost(url);
            if (ObjectUtils.isEmpty(config)) {
                config = new HttpClientConfig();
            }
            httpPost.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPost.addHeader(key, header.get(key));
            }
            if (Strings.CI.equals(contentType, ContentType.APPLICATION_JSON.getMimeType())) {
                EntityBuilder entityBuilder = EntityBuilder.create();
                if (MapUtils.isNotEmpty(param)) {
                    String json = JsonUtil.toJSONString(param).toString();
                    entityBuilder.setText(json);
                }
                entityBuilder.setContentType(ContentType.APPLICATION_JSON);
                HttpEntity requestEntity = entityBuilder.build();
                httpPost.setEntity(requestEntity);
            } else {
                List<NameValuePair> nvps = param.entrySet().stream().map(entry -> new BasicNameValuePair(entry.getKey(), ObjectUtils.isEmpty(entry.getValue()) ? null : entry.getValue().toString())).collect(Collectors.toList());
                try {
                    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(nvps, config.getCharset());
                    httpPost.setEntity(entity);
                } catch (Exception e) {
                    logger.error("HttpClient转换编码错误", e);
                    throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient转换编码错误: " + e.getMessage());
                }
            }
            HttpResponse response = httpClient.execute(httpPost);
            return getResponseStr(response, config);
        } catch (Exception e) {
            logger.error("HttpClient查询失败", e);
            throw new CrestException(SYSTEM_INNER_ERROR.code(), "HttpClient查询失败: " + e.getMessage());
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("HttpClient关闭连接失败", e);
            }
        }
    }

    /**
     * 请求截图服务并解析 multipart 响应中的元数据和图片内容
     */
    public static MultipartResponse postForScreenshot(
            String url, Map<String,String> body, HttpClientConfig config) throws IOException {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = buildHttpClient(url);
            HttpPost httpPost = new HttpPost(url);
            if (config == null) {
                config = new HttpClientConfig();
            }
            httpPost.setConfig(config.buildRequestConfig());
            Map<String, String> header = config.getHeader();
            for (String key : header.keySet()) {
                httpPost.addHeader(key, header.get(key));
            }
            EntityBuilder entityBuilder = EntityBuilder.create();
            String json = JsonUtil.toJSONString(body).toString();
            entityBuilder.setText(json);
            entityBuilder.setContentType(ContentType.APPLICATION_JSON);
            HttpEntity requestEntity = entityBuilder.build();
            httpPost.setEntity(requestEntity);


            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new CrestException(response.getStatusLine().getStatusCode(), response.toString());
                }
                HttpEntity entity = response.getEntity();
                byte[] bytes = EntityUtils.toByteArray(entity);          // raw bytes
                String contentType = response.getFirstHeader("Content-Type").getValue();
                if (contentType.startsWith("multipart/")) {
                    return MultipartParser.parse(bytes, contentType);   // see util below
                } else {
                    throw new IOException("unexpected response: " + contentType);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 截图服务 multipart 响应内容
     */
    @Data
    public static class MultipartResponse {
        Map<String,Object> metadata;
        byte[] image;
    }

    /**
     * multipart 响应解析器，用于拆分元数据和图片二进制内容
     */
    public class MultipartParser {
        /**
         * 按响应边界解析 multipart 响应体
         */
        public static   MultipartResponse parse(byte[] body, String contentType) throws IOException {
            String boundary = extractBoundary(contentType);
            String delim = "--" + boundary;
            byte[] delimBytes = delim.getBytes();
            MultipartResponse resp = new MultipartResponse();
            resp.metadata = new HashMap<>();

            int idx = 0;
            while (idx < body.length) {
                int start = indexOf(body, delimBytes, idx);
                if (start < 0) break;
                idx = start + delimBytes.length;
                if (idx + 1 < body.length && body[idx] == '-' && body[idx+1] == '-') break; // final boundary
                // skip CRLF
                if (body[idx] == '\r' && body[idx+1] == '\n') idx += 2;

                // read headers
                int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), idx);
                String headers = new String(body, idx, headerEnd - idx);
                idx = headerEnd + 4;

                boolean isImage = headers.contains("name=\"image\"");
                int nextBoundary = indexOf(body, delimBytes, idx);
                if (nextBoundary < 0) break;

                byte[] part = new byte[nextBoundary - idx - 2]; // strip trailing CRLF
                System.arraycopy(body, idx, part, 0, part.length);

                if (isImage) {
                    resp.image = part;
                } else {
                    String json = new String(part);
                    // 最简单把整个 JSON 字符串放到 metadata map；
                    // 你也可以用 Jackson/Gson 解析成具体字段
                    resp.metadata = JsonUtil.parseObject(json, Map.class);
                }
                idx = nextBoundary;
            }
            return resp;
        }

        /**
         * 从 multipart 内容类型中提取边界字符串
         */
        private static String extractBoundary(String contentType) {
            Pattern p = Pattern.compile("boundary=(.*)");
            Matcher m = p.matcher(contentType);
            if (m.find()) {
                return m.group(1);
            }
            throw new IllegalArgumentException("No boundary in content-type");
        }

        /**
         * 在字节数组中查找目标字节序列的位置
         */
        private static int indexOf(byte[] array, byte[] target, int start) {
            outer:
            for (int i = start; i <= array.length - target.length; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i+j] != target[j]) continue outer;
                }
                return i;
            }
            return -1;
        }
    }
}
