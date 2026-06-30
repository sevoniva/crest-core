package io.crest.datasource.provider;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import io.crest.extensions.datasource.dto.ApiDefinition;
import io.crest.extensions.datasource.dto.ApiDefinitionRequest;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.json.simple.JSONArray;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * API 数据源工具，负责把 API 配置转换为平台内部的数据表、字段和行数据
 * 这里会触发真实 HTTP 请求，并处理分页、动态参数、JSONPath 字段发现和结果拉平
 */
@SuppressWarnings({"deprecation", "unchecked", "rawtypes"})
public class ApiUtils {
    private static Configuration jsonPathConf = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.ALWAYS_RETURN_LIST)
            .build();
    private static String path = "['%s']";
    public static ObjectMapper objectMapper = CommonBeanFactory.getBean(ObjectMapper.class);

    private static TypeReference<List<Object>> listTypeReference = new TypeReference<List<Object>>() {
    };
    private static TypeReference<List<Map<String, Object>>> listForMapTypeReference = new TypeReference<List<Map<String, Object>>>() {
    };

    /**
     * 将用户录入的 JSONPath 规范化为 JsonPath 库可直接读取的根路径格式
     */
    private static String normalizeJsonPath(String jsonPath) {
        String path = StringUtils.trimToEmpty(jsonPath);
        if (StringUtils.isBlank(path)) {
            return path;
        }
        if (Strings.CS.equals(path, "$") || path.startsWith("$.") || path.startsWith("$[")) {
            return path;
        }
        if (path.startsWith("$")) {
            return "$." + path.substring(1);
        }
        if (path.startsWith(".")) {
            return "$" + path;
        }
        return "$." + path;
    }

    /**
     * 给列表根路径补充通配符，便于后续按行读取数组中的每个对象
     */
    private static String appendListWildcard(String jsonPath) {
        if (StringUtils.isBlank(jsonPath) || jsonPath.endsWith("[*]")) {
            return jsonPath;
        }
        return jsonPath + "[*]";
    }

    /**
     * 把 JsonPath 返回值统一成行集合，单对象结果会作为一行处理
     */
    private static List<Object> toRowList(Object object) {
        if (object instanceof List<?>) {
            return new ArrayList<>((List<?>) object);
        }
        List<Object> rows = new ArrayList<>();
        rows.add(object);
        return rows;
    }

    /**
     * 将复杂 JSON 值转换成字符串，失败时退回到对象的字符串表示，避免字段预览中断
     */
    private static String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return String.valueOf(object);
        }
    }

    /**
     * 字段发现完成后自动勾选叶子字段；中间节点只用于承载对象或数组层级
     */
    private static void checkLeafFields(List<Map<String, Object>> fields) {
        if (fields == null) {
            return;
        }
        for (Map<String, Object> field : fields) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) field.get("children");
            if (CollectionUtils.isEmpty(children)) {
                field.put("checked", true);
            } else {
                checkLeafFields(children);
            }
        }
    }

    /**
     * 将字段完整 JSONPath 转为相对于当前行对象的路径，供自定义根路径结果拉平使用
     */
    private static String toRowRelativeJsonPath(String fieldJsonPath, String rootJsonPath, String rootListJsonPath, String originName) {
        String normalizedFieldPath = normalizeJsonPath(fieldJsonPath);
        if (StringUtils.isBlank(normalizedFieldPath) && StringUtils.isNotBlank(originName)) {
            return "$['" + originName + "']";
        }
        String suffix = null;
        if (StringUtils.isNotBlank(rootListJsonPath) && normalizedFieldPath.startsWith(rootListJsonPath)) {
            suffix = normalizedFieldPath.substring(rootListJsonPath.length());
        } else if (StringUtils.isNotBlank(rootJsonPath) && normalizedFieldPath.startsWith(rootJsonPath)) {
            suffix = normalizedFieldPath.substring(rootJsonPath.length());
        }
        if (StringUtils.isBlank(suffix)) {
            return normalizedFieldPath;
        }
        if (suffix.startsWith(".[")) {
            return "$" + suffix.substring(1);
        }
        if (suffix.startsWith(".")) {
            return "$" + suffix;
        }
        return "$" + suffix;
    }

    /**
     * 将单元格值压平为无换行字符串；对象和多值数组保留 JSON 形态，单值数组按标量处理
     */
    private static String cellValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "";
            }
            if (list.size() == 1) {
                return cellValue(list.get(0));
            }
            return toJsonString(list).replaceAll("\n", " ").replaceAll("\r", " ");
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return toJsonString(value).replaceAll("\n", " ").replaceAll("\r", " ");
        }
        return String.valueOf(value).replaceAll("\n", " ").replaceAll("\r", " ");
    }

    /**
     * 解析 API 参数中的内置时间函数，当前支持时间戳、当天和昨天三类动态值
     */
    private static String formatTimeFunctionValue(String timeFormat) {
        if (StringUtils.isBlank(timeFormat)) {
            return null;
        }
        String[] timeFunction = timeFormat.split(" ", 2);
        String functionName = timeFunction[0];
        Calendar calendar = Calendar.getInstance();
        if (functionName.equalsIgnoreCase("currentTimestamp")) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (timeFunction.length < 2) {
            return null;
        }
        if (functionName.equalsIgnoreCase("yesterday")) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        } else if (!functionName.equalsIgnoreCase("currentDay")) {
            return null;
        }
        return new SimpleDateFormat(timeFunction[1]).format(calendar.getTime());
    }

    /**
     * 根据数据源配置生成可选 API 表清单，参数型 API 只作为动态参数来源，不暴露为数据表
     */
    public static List<DatasetTableDTO> getApiTables(DatasourceRequest datasourceRequest) throws CrestException {
        List<DatasetTableDTO> tableDescs = new ArrayList<>();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(apiDefinition.getType()) && apiDefinition.getType().equalsIgnoreCase("params")) {
                continue;
            }
            DatasetTableDTO datasetTableDTO = new DatasetTableDTO();
            datasetTableDTO.setTableName(apiDefinition.getDisplayTableName());
            datasetTableDTO.setName(apiDefinition.getName());
            datasetTableDTO.setDatasourceId(datasourceRequest.getDatasource().getId());
            tableDescs.add(datasetTableDTO);
        }
        return tableDescs;
    }

    /**
     * 构建 API 内部表名到展示表名的映射，供前端或导入流程展示用户配置的表名
     */
    public static Map<String, String> getTableNamesMap(String configration) throws CrestException {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode rootNode = objectMapper.readTree(configration);
            for (int i = 0; i < rootNode.size(); i++) {
                result.put(rootNode.get(i).get("name").asText(), rootNode.get(i).get("displayTableName").asText());
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }

        return result;
    }


    /**
     * 拉取指定 API 表的字段和预览数据。分页配置存在时会按页码或游标继续请求并合并结果
     */
    public static Map<String, Object> fetchApiResultField(DatasourceRequest datasourceRequest) throws CrestException {
        Map<String, Object> result = new HashMap<>();
        List<String[]> dataList = new ArrayList<>();
        List<TableField> fieldList = new ArrayList<>();
        ApiDefinition apiDefinition = getApiDefinition(datasourceRequest);
        if (apiDefinition == null) {
            CrestException.throwException("未找到");
        }
        // 分页型 API 需要先取第一页来确认字段，再根据分页策略继续补齐预览行
        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && !apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("empty")) {
            String response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
            fieldList = getTableFields(apiDefinition);
            result.put("fieldList", fieldList);
            if (apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("pageNumber")) {
                int pageCount = Integer.valueOf(JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString());
                int beginPage = Integer.valueOf(apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue());
                if (apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPathType().equalsIgnoreCase("totalNumber")) {
                    pageCount = pageCount / Integer.valueOf(apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()) + 1;
                }
                for (int i = beginPage; i <= pageCount; i++) {
                    apiDefinition.getRequest().getPage().getRequestData().get(0).setParameterDefaultValue(String.valueOf(i));
                    response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
                    dataList.addAll(fetchResult(response, apiDefinition));
                }
            }
            if (apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("cursor")) {
                dataList.addAll(fetchResult(response, apiDefinition));
                String cursor = null;
                try {
                    cursor = JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString();
                } catch (Exception e) {
                }
                while (cursor != null) {
                    apiDefinition.getRequest().getPage().getRequestData().get(0).setParameterDefaultValue(cursor);
                    response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
                    dataList.addAll(fetchResult(response, apiDefinition));
                    try {
                        if (cursor.equalsIgnoreCase(JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString())) {
                            cursor = null;
                        } else {
                            cursor = JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString();
                        }
                    } catch (Exception e) {
                        cursor = null;
                    }
                }
            }
            result.put("dataList", dataList);
            return result;
        } else {
            String response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
            fieldList = getTableFields(apiDefinition);
            result.put("fieldList", fieldList);
            dataList = fetchResult(response, apiDefinition);
            result.put("dataList", dataList);
            return result;
        }
    }


    /**
     * 返回 API 定义中已经保存的字段列表，不在此处重新推断字段结构
     */
    private static List<TableField> getTableFields(ApiDefinition apiDefinition) throws CrestException {
        return apiDefinition.getFields();
    }

    /**
     * 从数据源配置中找到当前请求表对应的 API 定义，并返回其已保存字段
     */
    public static List<TableField> getTableFields(DatasourceRequest datasourceRequest) throws CrestException {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };

        List<TableField> tableFields = new ArrayList<>();
        try {
            List<ApiDefinition> lists = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
            for (ApiDefinition apiDefinition : lists) {
                if (datasourceRequest.getTable().equalsIgnoreCase(apiDefinition.getDisplayTableName())) {
                    tableFields = getTableFields(apiDefinition);
                }
            }
        } catch (Exception e) {

        }
        return tableFields;
    }

    /**
     * 逐个探测 API 数据表的连通状态。单个 API 失败不会中断整体状态结果
     */
    public static String checkAPIStatus(DatasourceRequest datasourceRequest) throws Exception {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        List<ObjectNode> status = new ArrayList();
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null || (apiDefinition.getType() != null && apiDefinition.getType().equalsIgnoreCase("params"))) {
                continue;
            }
            datasourceRequest.setTable(apiDefinition.getName());
            ObjectNode apiItemStatuses = objectMapper.createObjectNode();
            try {
                data(datasourceRequest);
                apiItemStatuses.put("name", apiDefinition.getName());
                apiItemStatuses.put("status", "Success");
            } catch (Exception e) {
                LogUtil.error("API status Error: " + datasourceRequest.getDatasource().getName() + "-" + apiDefinition.getName(), e);
                apiItemStatuses.put("name", apiDefinition.getName());
                apiItemStatuses.put("status", "Error");
            }
            status.add(apiItemStatuses);
        }
        return JsonUtil.toJSONString(status).toString();
    }

    /**
     * 执行当前请求表对应的 API，并把响应转换为数据行
     */
    private static List<String[]> data(DatasourceRequest datasourceRequest) throws Exception {
        ApiDefinition apiDefinition = getApiDefinition(datasourceRequest);
        if (apiDefinition == null) {
            CrestException.throwException("未找到");
        }
        String response = execHttpRequest(true, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
        return fetchResult(response, apiDefinition);
    }

    /**
     * 根据 API 定义组装并执行 HTTP 请求。请求头、URL 参数和请求体中的动态参数会递归调用参数型 API 取值
     */
    public static String execHttpRequest(boolean preview, ApiDefinition api, int socketTimeout, List<ApiDefinition> paramsList) {
        ApiDefinition apiDefinition = new ApiDefinition();
        BeanUtils.copyBean(apiDefinition, api);

        // 页码分页会把内置分页参数替换到 URL 和请求定义中，后续请求复用替换后的定义
        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("pageNumber")) {
            apiDefinition.setUrl(apiDefinition.getUrl().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()));
            apiDefinition.setRequest(JsonUtil.parseObject(JsonUtil.toJSONString(apiDefinition.getRequest()).toString().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()), ApiDefinitionRequest.class));
        }

        // 游标分页的默认游标允许为空，表示第一次请求由接口返回下一轮游标
        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("cursor")) {
            apiDefinition.setUrl(apiDefinition.getUrl().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()));
            String defaultCursor = apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue();
            apiDefinition.setRequest(JsonUtil.parseObject(JsonUtil.toJSONString(apiDefinition.getRequest()).toString().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), StringUtils.isEmpty(defaultCursor) ? "" : defaultCursor).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()), ApiDefinitionRequest.class));
        }


        String response = "";
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        httpClientConfig.setSocketTimeout(socketTimeout * 1000);
        ApiDefinitionRequest apiDefinitionRequest = apiDefinition.getRequest();
        // 头部参数支持固定值、参数型 API 返回值、自定义模板和内置时间函数
        for (Map header : apiDefinitionRequest.getHeaders()) {
            if (header.get("name") != null && StringUtils.isNotEmpty(header.get("name").toString()) && header.get("value") != null && StringUtils.isNotEmpty(header.get("value").toString())) {
                if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("params")) {
                    String param = header.get("value").toString();
                    for (ApiDefinition definition : paramsList) {
                        for (int i = 0; i < definition.getFields().size(); i++) {
                            TableField field = definition.getFields().get(i);
                            if (field.getName().equalsIgnoreCase(param)) {
                                String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                List<String[]> dataList = fetchResult(resultStr, definition);
                                if (dataList.size() > 0) {
                                    if (dataList.size() == 1) {
                                        httpClientConfig.addHeader(header.get("name").toString(), dataList.get(0)[i]);
                                    } else {
                                        List<String> datas = new ArrayList<>();
                                        for (String[] data : dataList) {
                                            datas.add(data[i]);
                                        }
                                        httpClientConfig.addHeader(header.get("name").toString(), JsonUtil.toJSONString(datas).toString());
                                    }
                                }
                            }
                        }
                    }
                } else if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("custom")) {
                    List<String> params = new ArrayList<>();
                    String regex = "\\$\\{(.*?)\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(header.get("value").toString());
                    while (matcher.find()) {
                        params.add(matcher.group(1));
                    }
                    String result = header.get("value").toString();
                    for (String param : params) {
                        for (ApiDefinition definition : paramsList) {
                            for (int i = 0; i < definition.getFields().size(); i++) {
                                TableField field = definition.getFields().get(i);
                                if (field.getName().equalsIgnoreCase(param)) {
                                    String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                    if (dataList.size() > 0) {
                                        if (dataList.size() == 1) {
                                            result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                        } else {
                                            List<String> datas = new ArrayList<>();
                                            for (String[] data : dataList) {
                                                datas.add(data[i]);
                                            }
                                            result = result.replace("${" + param + "}", JsonUtil.toJSONString(datas).toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    httpClientConfig.addHeader(header.get("name").toString(), result);
                } else if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("timeFun")) {
                    String timeValue = formatTimeFunctionValue(header.get("value").toString());
                    if (StringUtils.isNotEmpty(timeValue)) {
                        httpClientConfig.addHeader(header.get("name").toString(), timeValue);
                    }
                } else {
                    httpClientConfig.addHeader(header.get("name").toString(), header.get("value").toString());
                }

            }
        }
        if (apiDefinitionRequest.getAuthManager() != null
                && StringUtils.isNotBlank(apiDefinitionRequest.getAuthManager().getUsername())
                && StringUtils.isNotBlank(apiDefinitionRequest.getAuthManager().getPassword())
                && apiDefinitionRequest.getAuthManager().getVerification().equals("Basic Auth")) {
            String authValue = "Basic " + Base64.getUrlEncoder().encodeToString((apiDefinitionRequest.getAuthManager().getUsername()
                    + ":" + apiDefinitionRequest.getAuthManager().getPassword()).getBytes());
            httpClientConfig.addHeader("Authorization", authValue);
        }

        List<String> params = new ArrayList<>();
        // URL 查询参数与请求头保持同样的动态参数语义，最终追加到请求地址后
        for (Map<String, String> argument : apiDefinition.getRequest().getArguments()) {
            if (StringUtils.isNotEmpty(argument.get("name")) && StringUtils.isNotEmpty(argument.get("value"))) {
                if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("params")) {
                    String param = argument.get("value").toString();
                    for (ApiDefinition definition : paramsList) {
                        for (int i = 0; i < definition.getFields().size(); i++) {
                            TableField field = definition.getFields().get(i);
                            if (field.getOriginName().equalsIgnoreCase(param)) {
                                String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                List<String[]> dataList = fetchResult(resultStr, definition);
                                if (dataList.size() > 0) {
                                    params.add(argument.get("name") + "=" + dataList.get(0)[i]);
                                }
                            }
                        }
                    }
                } else if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("custom")) {
                    List<String> arrayList = new ArrayList<>();
                    String regex = "\\$\\{(.*?)\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(argument.get("value").toString());
                    while (matcher.find()) {
                        arrayList.add(matcher.group(1));
                    }
                    String result = argument.get("value").toString();
                    for (String param : arrayList) {
                        for (ApiDefinition definition : paramsList) {
                            for (int i = 0; i < definition.getFields().size(); i++) {
                                TableField field = definition.getFields().get(i);
                                if (field.getName().equalsIgnoreCase(param)) {
                                    String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                    if (dataList.size() > 0) {
                                        result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                    }
                                }
                            }
                        }
                    }
                    params.add(argument.get("name") + "=" + result);
                } else if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("timeFun")) {
                    String timeValue = formatTimeFunctionValue(argument.get("value").toString());
                    if (StringUtils.isNotEmpty(timeValue)) {
                        params.add(argument.get("name") + "=" + timeValue);
                    }
                } else {
                    params.add(argument.get("name") + "=" + URLEncoder.encode(argument.get("value")));
                }
            }
        }
        if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(params)) {
            apiDefinition.setUrl(apiDefinition.getUrl() + "?" + StringUtils.join(params, "&"));
        }

        switch (apiDefinition.getMethod()) {
            case "GET":
                response = HttpClientUtil.get(apiDefinition.getUrl().trim(), httpClientConfig);
                break;
            case "POST":
                if (!apiDefinitionRequest.getBody().keySet().contains("type")) {
                    CrestException.throwException("请求类型不能为空");
                }
                String type = apiDefinitionRequest.getBody().get("type").toString();
                if (Strings.CS.equalsAny(type, "JSON", "XML", "Raw")) {
                    String raw = null;
                    if (apiDefinitionRequest.getBody().get("raw") != null) {
                        raw = apiDefinitionRequest.getBody().get("raw").toString();

                        List<String> bodYparams = new ArrayList<>();
                        String regex = "\\$\\{(.*?)\\}";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(raw);
                        while (matcher.find()) {
                            bodYparams.add(matcher.group(1));
                        }
                        for (String param : bodYparams) {
                            if (param.equalsIgnoreCase("currentTimestamp")) {
                                raw = raw.replace("${" + param + "}", String.valueOf(System.currentTimeMillis()));
                                continue;
                            }
                            for (ApiDefinition definition : paramsList) {
                                for (int i = 0; i < definition.getFields().size(); i++) {
                                    TableField field = definition.getFields().get(i);
                                    if (field.getOriginName().equalsIgnoreCase(param)) {
                                        String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                        List<String[]> dataList = fetchResult(resultStr, definition);
                                        if (dataList.size() > 0) {
                                            if (dataList.size() == 1) {
                                                raw = raw.replace("${" + param + "}", dataList.get(0)[i]);
                                            } else {
                                                List<String> datas = new ArrayList<>();
                                                for (String[] data : dataList) {
                                                    datas.add(data[i]);
                                                }
                                                raw = raw.replace("${" + param + "}", JsonUtil.toJSONString(datas).toString());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        response = HttpClientUtil.post(apiDefinition.getUrl(), raw, httpClientConfig);
                    }
                }
                if (Strings.CS.equalsAny(type, "Form_Data", "WWW_FORM")) {
                    if (apiDefinitionRequest.getBody().get("kvs") != null) {
                        Map<String, String> body = new HashMap<>();
                        TypeReference<List<JsonNode>> listTypeReference = new TypeReference<List<JsonNode>>() {
                        };
                        List<JsonNode> rootNode = null;
                        try {
                            rootNode = objectMapper.readValue(JsonUtil.toJSONString(apiDefinition.getRequest().getBody().get("kvs")).toString(), listTypeReference);
                        } catch (Exception e) {
                            io.crest.utils.LogUtil.error(e.getMessage(), e);
                            CrestException.throwException(e);
                        }
                        for (JsonNode jsonNode : rootNode) {
                            if (jsonNode.has("name") && jsonNode.has("value")) {
                                if (jsonNode.get("value") != null && StringUtils.isNotEmpty(jsonNode.get("value").asText())) {
                                    if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("params")) {
                                        String param = jsonNode.get("value").asText();
                                        for (ApiDefinition definition : paramsList) {
                                            for (int i = 0; i < definition.getFields().size(); i++) {
                                                TableField field = definition.getFields().get(i);
                                                if (field.getOriginName().equalsIgnoreCase(param)) {
                                                    String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                                    if (dataList.size() > 0) {
                                                        if (dataList.size() == 1) {
                                                            body.put(jsonNode.get("name").asText(), dataList.get(0)[i]);
                                                        } else {
                                                            List<String> datas = new ArrayList<>();
                                                            for (String[] data : dataList) {
                                                                datas.add(data[i]);
                                                            }
                                                            body.put(jsonNode.get("name").asText(), JsonUtil.toJSONString(datas).toString());
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("custom")) {
                                        List<String> bodYparams = new ArrayList<>();
                                        String regex = "\\$\\{(.*?)\\}";
                                        Pattern pattern = Pattern.compile(regex);
                                        Matcher matcher = pattern.matcher(jsonNode.get("value").asText());
                                        while (matcher.find()) {
                                            bodYparams.add(matcher.group(1));
                                        }
                                        String result = jsonNode.get("value").asText();
                                        for (String param : bodYparams) {
                                            for (ApiDefinition definition : paramsList) {
                                                for (int i = 0; i < definition.getFields().size(); i++) {
                                                    TableField field = definition.getFields().get(i);
                                                    if (field.getOriginName().equalsIgnoreCase(param)) {
                                                        String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), paramsList);
                                                        List<String[]> dataList = fetchResult(resultStr, definition);
                                                        if (dataList.size() > 0) {
                                                            if (dataList.size() == 1) {
                                                                result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                                            } else {
                                                                List<String> datas = new ArrayList<>();
                                                                for (String[] data : dataList) {
                                                                    datas.add(data[i]);
                                                                }
                                                                result = result.replace("${" + param + "}", JsonUtil.toJSONString(datas).toString());
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        body.put(jsonNode.get("name").asText(), result);
                                     } else if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("timeFun")) {
                                         String timeValue = formatTimeFunctionValue(jsonNode.get("value").asText());
                                         if (StringUtils.isNotEmpty(timeValue)) {
                                             body.put(jsonNode.get("name").asText(), timeValue);
                                         }
                                     } else {
                                         body.put(jsonNode.get("name").asText(), jsonNode.get("value").asText());
                                    }
                                }
                            }
                        }
                        response = HttpClientUtil.post(apiDefinition.getUrl(), body, httpClientConfig);
                    }
                }
                break;
            default:
                break;
        }
        return response;
    }

    /**
     * 给字段树填充前 100 条预览值，避免字段发现阶段把完整响应体塞进配置对象
     */
    private static void previewNum(List<Map<String, Object>> fields, String response) {
        int previewNum = 100;
        for (Map<String, Object> field : fields) {
            JSONArray newArray = new JSONArray();
            if (field.get("value") != null) {
                Object object = JsonPath.using(jsonPathConf).parse(response).read(field.get("jsonPath").toString());
                int i = 0;
                if (object instanceof List) {
                    for (Object o : (List<String>) object) {
                        if (Objects.isNull(o)) {
                            newArray.add("");
                        } else {
                            newArray.add(o.toString());
                        }
                        i++;
                        if (i >= previewNum) {
                            break;
                        }
                    }
                } else {
                    if (object != null) {
                        newArray.add(object.toString());
                    }
                }
                field.put("value", newArray);
            } else {
                List<Map<String, Object>> childrenFields = (List<Map<String, Object>>) field.get("children");
                previewNum(childrenFields, response);
            }
        }
    }

    /**
     * 发起一次 API 预览请求，并根据响应结构回填可选字段树
     */
    public static ApiDefinition checkApiDefinition(DatasourceRequest datasourceRequest) throws CrestException {
        ApiDefinition apiDefinition = new ApiDefinition();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        if (!CollectionUtils.isEmpty(apiDefinitionList)) {
            for (ApiDefinition definition : apiDefinitionList) {
                if (definition != null && (definition.getType() == null || !definition.getType().equalsIgnoreCase("params"))) {
                    apiDefinition = definition;
                }
            }
        }
        String response = execHttpRequest(true, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
        return checkApiDefinition(apiDefinition, response);
    }

    /**
     * 根据 API 响应推断字段树。启用自定义 JSONPath 时，只围绕选中的根路径构造字段
     */
    private static ApiDefinition checkApiDefinition(ApiDefinition apiDefinition, String response) throws CrestException {
        if (StringUtils.isEmpty(response)) {
            CrestException.throwException("该请求返回数据为空");
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        boolean useSelectedJsonPath = apiDefinition.isUseJsonPath()
                && StringUtils.isNotBlank(apiDefinition.getJsonPath());
        if (!useSelectedJsonPath) {
            String rootPath;
            if (response.startsWith("[")) {
                rootPath = "$[*]";
                JsonNode jsonArray = null;
                try {
                    jsonArray = objectMapper.readTree(response);
                } catch (Exception e) {
                    CrestException.throwException(e);
                }
                for (Object o : jsonArray) {
                    handleStr(apiDefinition, o.toString(), fields, rootPath);
                }
            } else {
                rootPath = "$";
                handleStr(apiDefinition, response, fields, rootPath);
            }
            previewNum(fields, response);
            apiDefinition.setJsonFields(fields);
            return apiDefinition;
        } else {
            List<Object> currentData = new ArrayList<>();
            String jsonPath = normalizeJsonPath(apiDefinition.getJsonPath());
            apiDefinition.setJsonPath(jsonPath);
            boolean jsonPathResultIsList;
            try {
                Object object = JsonPath.read(response, jsonPath);
                jsonPathResultIsList = object instanceof List;
                currentData = toRowList(object);
            } catch (Exception e) {
                CrestException.throwException(e);
                return apiDefinition;
            }
            int i = 0;
            try {
                Object data = currentData.get(0);
                if (!(data instanceof Map)) {
                    CrestException.throwException("数据不符合规范");
                }
            } catch (Exception e) {
                CrestException.throwException("数据不符合规范, " + e.getMessage());
            }
            String rootPath = jsonPathResultIsList ? appendListWildcard(jsonPath) : jsonPath;
            for (Object data : currentData) {
                if (i >= apiDefinition.getPreviewNum()) {
                    break;
                }
                handleStr(apiDefinition, toJsonString(data), fields, rootPath);
                i++;
            }
            checkLeafFields(fields);
            previewNum(fields, response);
            apiDefinition.setJsonFields(fields);
            return apiDefinition;
        }
    }


    /**
     * 递归解析 JSON 字符串，把对象字段、数组字段和标量字段转成前端可配置的字段树节点
     */
    private static void handleStr(ApiDefinition apiDefinition, String jsonStr, List<Map<String, Object>> fields, String rootPath) throws CrestException {
        if (jsonStr.startsWith("[")) {
            TypeReference<List<Object>> listTypeReference = new TypeReference<List<Object>>() {
            };
            List<Object> jsonArray = null;

            try {
                jsonArray = objectMapper.readValue(jsonStr, listTypeReference);
            } catch (Exception e) {
                CrestException.throwException(e);
            }
            for (Object o : jsonArray) {
                handleStr(apiDefinition, o.toString(), fields, rootPath);
            }
        } else {
            JsonNode jsonNode = null;
            try {
                jsonNode = objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                CrestException.throwException(e);
            }
            Iterator<String> fieldNames = jsonNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String value = jsonNode.get(fieldName).toString();
                if (StringUtils.isNotEmpty(value) && !value.startsWith("[") && !value.startsWith("{")) {
                    value = jsonNode.get(fieldName).asText();
                }
                if (StringUtils.isNotEmpty(value) && value.startsWith("[")) {
                    Map<String, Object> o = new HashMap<>();
                    try {
                        JsonNode jsonArray = objectMapper.readTree(value);
                        List<Map<String, Object>> childrenField = new ArrayList<>();
                        for (JsonNode node : jsonArray) {
                            if (StringUtils.isNotEmpty(node.toString()) && !node.toString().startsWith("[") && !node.toString().startsWith("{")) {
                                throw new Exception(node + "is not json type");
                            }
                        }
                        for (JsonNode node : jsonArray) {
                            handleStr(apiDefinition, node.toString(), childrenField, rootPath + "." + String.format(path, fieldName) + "[*]");
                        }
                        o.put("children", childrenField);
                        o.put("childrenDataType", "LIST");
                    } catch (Exception e) {
                        JSONArray array = new JSONArray();
                        array.add(StringUtils.isNotEmpty(jsonNode.get(fieldName).toString()) ? jsonNode.get(fieldName).toString() : "");
                        o.put("value", array);
                    }
                    o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                    setProperty(apiDefinition, o, fieldName);
                    if (!hasItem(apiDefinition, fields, o)) {
                        fields.add(o);
                    }
                } else if (StringUtils.isNotEmpty(value) && value.startsWith("{")) {
                    try {
                        JsonNode jsonNode1 = objectMapper.readTree(value);
                        List<Map<String, Object>> children = new ArrayList<>();
                        handleStr(apiDefinition, value, children, rootPath + "." + String.format(path, fieldName));
                        Map<String, Object> o = new HashMap<>();
                        o.put("children", children);
                        o.put("childrenDataType", "OBJECT");
                        o.put("jsonPath", rootPath + "." + fieldName);
                        setProperty(apiDefinition, o, fieldName);
                        if (!hasItem(apiDefinition, fields, o)) {
                            fields.add(o);
                        }
                    } catch (Exception e) {
                        Map<String, Object> o = new HashMap<>();
                        o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                        setProperty(apiDefinition, o, fieldName);
                        JSONArray array = new JSONArray();
                        array.add(StringUtils.isNotEmpty(value) ? value : "");
                        o.put("value", array);
                        if (!hasItem(apiDefinition, fields, o)) {
                            fields.add(o);
                        }
                    }
                } else {
                    Map<String, Object> o = new HashMap<>();
                    o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                    setProperty(apiDefinition, o, fieldName);
                    JSONArray array = new JSONArray();
                    array.add(StringUtils.isNotEmpty(value) ? value : "");
                    o.put("value", array);
                    if (!hasItem(apiDefinition, fields, o)) {
                        fields.add(o);
                    }
                }

            }
        }
    }

    /**
     * 给字段树节点写入默认属性，并在非自定义 JSONPath 模式下恢复用户已保存的字段配置
     */
    private static void setProperty(ApiDefinition apiDefinition, Map<String, Object> o, String s) {
        o.put("originName", s);
        o.put("name", s);
        o.put("type", "STRING");
        o.put("size", 65535);
        o.put("extractedFieldType", 0);
        o.put("fieldType", 0);
        o.put("checked", false);
        if (!apiDefinition.isUseJsonPath()) {
            for (TableField field : apiDefinition.getFields()) {
                if (!ObjectUtils.isEmpty(o.get("jsonPath")) && StringUtils.isNotEmpty(field.getJsonPath()) && field.getJsonPath().equals(o.get("jsonPath").toString())) {
                    o.put("checked", true);
                    o.put("name", field.getName());
                    o.put("primaryKey", field.isPrimaryKey());
                    o.put("length", field.getLength());
                    o.put("extractedFieldType", field.getExtractedFieldType());
                }
            }
        }
    }

    /**
     * 判断字段树中是否已有相同 JSONPath 节点；存在时合并字段结构和预览值
     */
    private static boolean hasItem(ApiDefinition apiDefinition, List<Map<String, Object>> fields, Map<String, Object> item) throws CrestException {
        boolean has = false;
        for (Map<String, Object> field : fields) {
            if (field.get("jsonPath").equals(item.get("jsonPath"))) {
                has = true;
                mergeField(field, item);
                mergeValue(field, apiDefinition, item);
                break;
            }
        }

        return has;
    }


    /**
     * 合并重复字段节点的子节点结构，确保多个样本对象中出现的新字段不会丢失
     */
    private static void mergeField(Map<String, Object> field, Map<String, Object> item) throws CrestException {
        if (item.get("children") != null) {
            List<Map<String, Object>> fieldChildren = null;
            List<Map<String, Object>> itemChildren = null;
            try {
                fieldChildren = objectMapper.readValue(JsonUtil.toJSONString(field.get("children")).toString(), listForMapTypeReference);
                itemChildren = objectMapper.readValue(JsonUtil.toJSONString(item.get("children")).toString(), listForMapTypeReference);
            } catch (Exception e) {
                CrestException.throwException(e);
            }
            if (fieldChildren == null) {
                fieldChildren = new ArrayList<>();
            }
            for (Map<String, Object> itemChild : itemChildren) {
                boolean hasKey = false;
                for (Map<String, Object> fieldChild : fieldChildren) {
                    if (itemChild.get("jsonPath").toString().equals(fieldChild.get("jsonPath").toString())) {
                        mergeField(fieldChild, itemChild);
                        hasKey = true;
                    }
                }
                if (!hasKey) {
                    fieldChildren.add(itemChild);
                }
            }
            field.put("children", fieldChildren);
        }
    }

    /**
     * 合并重复字段节点的预览值，递归处理对象和数组子节点
     */
    private static void mergeValue(Map<String, Object> field, ApiDefinition apiDefinition, Map<String, Object> item) throws CrestException {
        TypeReference<JSONArray> listTypeReference = new TypeReference<JSONArray>() {
        };
        try {
            if (!ObjectUtils.isEmpty(field.get("value")) && !ObjectUtils.isEmpty(item.get("value"))) {
                JSONArray array = objectMapper.readValue(JsonUtil.toJSONString(field.get("value")).toString(), listTypeReference);
                array.add(objectMapper.readValue(JsonUtil.toJSONString(item.get("value")).toString(), listTypeReference).get(0));
                field.put("value", array);
            }
            if (!ObjectUtils.isEmpty(field.get("children")) && !ObjectUtils.isEmpty(item.get("children"))) {
                List<Map<String, Object>> fieldChildren = objectMapper.readValue(JsonUtil.toJSONString(field.get("children")).toString(), listForMapTypeReference);
                List<Map<String, Object>> itemChildren = objectMapper.readValue(JsonUtil.toJSONString(item.get("children")).toString(), listForMapTypeReference);
                List<Map<String, Object>> fieldArrayChildren = new ArrayList<>();
                for (Map<String, Object> fieldChild : fieldChildren) {
                    Map<String, Object> find = null;
                    for (Map<String, Object> itemChild : itemChildren) {
                        if (fieldChild.get("jsonPath").toString().equals(itemChild.get("jsonPath").toString())) {
                            find = itemChild;
                        }
                    }
                    if (find != null) {
                        mergeValue(fieldChild, apiDefinition, find);
                    }
                    fieldArrayChildren.add(fieldChild);
                }
                field.put("children", fieldArrayChildren);
            }
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }

    }

    /**
     * 按 API 字段配置从响应体中抽取行数据。自定义 JSONPath 模式按根路径逐行读取，普通模式按列补齐
     */
    private static List<String[]> fetchResult(String result, ApiDefinition apiDefinition) {
        List<String[]> dataList = new LinkedList<>();
        if (apiDefinition.isUseJsonPath()) {
            String jsonPath = normalizeJsonPath(apiDefinition.getJsonPath());
            apiDefinition.setJsonPath(jsonPath);
            Object object = JsonPath.read(result, jsonPath);
            boolean jsonPathResultIsList = object instanceof List;
            String rootListJsonPath = jsonPathResultIsList ? appendListWildcard(jsonPath) : jsonPath;
            List<Object> currentData = toRowList(object);
            for (Object data : currentData) {
                String[] row = new String[apiDefinition.getFields().size()];
                int i = 0;
                String rowJson = toJsonString(data);
                for (TableField field : apiDefinition.getFields()) {
                    String relativePath = toRowRelativeJsonPath(
                            field.getJsonPath(),
                            jsonPath,
                            rootListJsonPath,
                            field.getOriginName()
                    );
                    Object value;
                    try {
                        value = JsonPath.using(jsonPathConf).parse(rowJson).read(relativePath);
                    } catch (Exception e) {
                        value = data instanceof Map<?, ?> map ? map.get(field.getOriginName()) : null;
                    }
                    row[i] = cellValue(value);
                    i++;
                }
                dataList.add(row);
            }
        } else {
            List<String> jsonPaths = apiDefinition.getFields().stream().map(TableField::getJsonPath).collect(Collectors.toList());
            Long maxLength = 0l;
            List<List<String>> columnDataList = new ArrayList<>();
            for (int i = 0; i < jsonPaths.size(); i++) {
                List<String> data = new ArrayList<>();
                Object object = JsonPath.using(jsonPathConf).parse(result).read(jsonPaths.get(i));
                if (object instanceof List) {
                    for (Object o : (List<String>) object) {
                        if (Objects.isNull(o)) {
                            data.add("");
                        } else {
                            data.add(o.toString());
                        }
                    }
                } else {
                    if (object != null) {
                        data.add(object.toString());
                    }
                }
                maxLength = maxLength > data.size() ? maxLength : data.size();
                columnDataList.add(data);
            }
            for (int i = 0; i < maxLength; i++) {
                String[] row = new String[apiDefinition.getFields().size()];
                dataList.add(row);
            }
            for (int i = 0; i < columnDataList.size(); i++) {
                for (int j = 0; j < columnDataList.get(i).size(); j++) {
                    dataList.get(j)[i] = Optional.ofNullable(String.valueOf(columnDataList.get(i).get(j))).orElse("").replaceAll("\n", " ").replaceAll("\r", " ");
                }
            }
        }
        return dataList;
    }


    /**
     * 返回配置中的参数型 API 定义，这些定义只参与动态参数求值，不作为最终查询表
     */
    private static List<ApiDefinition> params(DatasourceRequest datasourceRequest) {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionListTemp = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        return apiDefinitionListTemp.stream().filter(apiDefinition -> apiDefinition != null && apiDefinition.getType() != null && apiDefinition.getType().equalsIgnoreCase("params")).collect(Collectors.toList());
    }

    /**
     * 根据请求表名查找唯一 API 定义，内部名称和展示名称都可匹配
     */
    private static ApiDefinition getApiDefinition(DatasourceRequest datasourceRequest) throws CrestException {
        List<ApiDefinition> apiDefinitionList = new ArrayList<>();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionListTemp = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);

        if (!CollectionUtils.isEmpty(apiDefinitionListTemp)) {
            for (ApiDefinition apiDefinition : apiDefinitionListTemp) {
                if (apiDefinition == null || apiDefinition.getType() == null || apiDefinition.getType().equalsIgnoreCase("params")) {
                    continue;
                }
                if (apiDefinition.getDisplayTableName().equalsIgnoreCase(datasourceRequest.getTable()) || apiDefinition.getName().equalsIgnoreCase(datasourceRequest.getTable())) {
                    apiDefinitionList.add(apiDefinition);
                }

            }
        }
        if (CollectionUtils.isEmpty(apiDefinitionList)) {
            CrestException.throwException("未找到API数据表");
        }
        if (apiDefinitionList.size() > 1) {
            CrestException.throwException("存在重名的API数据表");
        }
        ApiDefinition find = null;
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null) {
                continue;
            }
            if (apiDefinition.getName().equalsIgnoreCase(datasourceRequest.getTable()) || apiDefinition.getDisplayTableName().equalsIgnoreCase(datasourceRequest.getTable())) {
                find = apiDefinition;
            }
        }
        return find;
    }

}
