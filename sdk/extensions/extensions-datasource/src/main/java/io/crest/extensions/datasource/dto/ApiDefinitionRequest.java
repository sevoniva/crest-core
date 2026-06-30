package io.crest.extensions.datasource.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
/**
 * API 数据源定义请求，承载请求头、请求体、参数、鉴权和分页配置
 */
public class ApiDefinitionRequest {
    /**
     * API 请求头参数列表
     */
    private List<Map<String, String>> headers = new ArrayList<>();
    /**
     * API 请求体配置
     */
    private Map<String, Object> body = new HashMap<>();
    /**
     * 查询或表单参数列表
     */
    private List<Map<String, String>> arguments = new ArrayList<>();
    /**
     * 路径参数列表
     */
    private List<Map<String, String>> rest = new ArrayList<>();
    /**
     * API 鉴权配置
     */
    private AuthManager authManager = new AuthManager();
    /**
     * API 分页解析配置
     */
    private Page page = new Page();


    @Data
    /**
     * API 鉴权账号配置
     */
    public static class AuthManager {
        /**
         * 鉴权密码
         */
        private String password;
        /**
         * 鉴权用户名
         */
        private String username;
        /**
         * 鉴权校验方式
         */
        private String verification = "";
    }

    @Data
    /**
     * API 分页配置，包含分页类型和请求响应映射
     */
    public static class Page {
        /**
         * 分页类型，默认表示不启用分页
         */
        private String pageType = "empty";
        /**
         * 分页请求参数映射
         */
        private List<RequestItem> requestData;
        /**
         * 分页响应字段解析映射
         */
        private List<ResponseItem> responseData;
    }

    @Data
    /**
     * 分页请求参数映射项
     */
    public static class RequestItem {
        /**
         * 页面展示的参数名称
         */
        private String parameterName;
        /**
         * 内置分页变量名称
         */
        private String builtInParameterName;
        /**
         * 实际请求参数名称
         */
        private String requestParameterName;
        /**
         * 请求参数默认值
         */
        private String parameterDefaultValue;
    }

    @Data
    /**
     * 分页响应字段解析项
     */
    public static class ResponseItem {
        /**
         * 页面展示的响应字段名称
         */
        private String parameterName;
        /**
         * 响应数据中的解析路径
         */
        private String resolutionPath;
        /**
         * 解析路径对应的字段类型
         */
        private String resolutionPathType;
    }

}
