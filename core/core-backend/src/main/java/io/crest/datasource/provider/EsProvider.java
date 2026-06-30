package io.crest.datasource.provider;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.crest.dataset.utils.FieldUtils;
import io.crest.datasource.dto.es.EsResponse;
import io.crest.datasource.dto.es.Request;
import io.crest.datasource.type.Es;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.i18n.Translator;
import io.crest.utils.HttpClientConfig;
import io.crest.utils.HttpClientUtil;
import io.crest.utils.JsonUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

// 提供 Elasticsearch 数据源访问能力
public class EsProvider extends Provider {

    // 返回 Elasticsearch 数据源 schema 列表
    @Override
    public List<String> getSchema(DatasourceRequest datasourceRequest) {
        return new ArrayList<>();
    }

    // 查询 Elasticsearch 可用索引表
    @Override
    public List<DatasetTableDTO> tables(DatasourceRequest datasourceRequest) {
        List<DatasetTableDTO> tables = new ArrayList<>();
        try {
            String response = execQuery(datasourceRequest, "show tables", "?format=json");
            tables = fetchTables(response);
            tables = tables.stream().filter(table -> StringUtils.isNotEmpty(table.getTableName()) && !table.getTableName().startsWith(".")).collect(Collectors.toList());
            tables.forEach(table -> {
                table.setDatasourceId(datasourceRequest.getDatasource().getId());
            });
        } catch (Exception e) {
            e.getMessage();
            CrestException.throwException(e);
        }
        return tables;
    }

    // 获取 Elasticsearch 连接对象占位
    @Override
    public ConnectionObj getConnection(DatasourceDTO coreDatasource) throws Exception {
        return null;
    }

    // 检测 Elasticsearch 数据源连接状态
    @Override
    public String checkStatus(DatasourceRequest datasourceRequest) throws Exception {
        Es es = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Es.class);
        String response = execGetQuery(datasourceRequest);
        if (JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("error") != null) {
            throw new Exception(JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("error").get("reason").getAsString());
        }
        String version = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("version").get("number").getAsString();
        String[] versionList = version.split("\\.");
        if (Integer.valueOf(versionList[0]) < 7 && Integer.valueOf(versionList[1]) < 3) {
            throw new Exception(Translator.get("i18n_es_limit"));
        }
        if (Integer.valueOf(versionList[0]) == 6) {
            es.setUri("_sql");
        }
        if (Integer.valueOf(versionList[0]) > 6) {
            es.setUri("_sql");
        }
        datasourceRequest.getDatasource().setConfiguration(JsonUtil.toJSONString(es).toString());
        tables(datasourceRequest);
        return "Success";
    }

    // 执行查询并返回数据和字段
    @Override
    public Map<String, Object> fetchResultField(DatasourceRequest datasourceRequest) {
        Map<String, Object> result = new HashMap<>();
        try {
            String response = execQuery(datasourceRequest, datasourceRequest.getQuery(), "?format=json");
            result.put("data", fetchResultData(response));
            result.put("fields", fetchResultField4Sql(response));
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e);
        }
        return result;
    }

    // 获取 Elasticsearch 表或 SQL 的字段信息
    @Override
    public List<TableField> fetchTableField(DatasourceRequest datasourceRequest) {
        List<TableField> tableFields = new ArrayList<>();
        try {
            String sql;
            if (datasourceRequest.getTable() != null) {
                if (!tables(datasourceRequest).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList()).contains(datasourceRequest.getTable())) {
                    CrestException.throwException("无效的表名！");
                }
                sql = "select * from \"" + datasourceRequest.getTable() + "\" limit 0";
            } else {
                sql = datasourceRequest.getQuery();
            }
            String response = execQuery(datasourceRequest, sql, "?format=json");
            tableFields = fetchResultField4Sql(response);
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return tableFields;
    }


    // 隐藏数据源敏感配置
    @Override
    public void hidePW(DatasourceDTO datasourceDTO) {
    }


    // 解析查询响应中的数据行
    private List<String[]> fetchResultData(String response) throws Exception {
        EsResponse esResponse = new Gson().fromJson(response, EsResponse.class);
        return fetchResultData(esResponse);
    }

    // 从 Elasticsearch 响应对象提取数据行
    private List<String[]> fetchResultData(EsResponse esResponse) throws Exception {
        List<String[]> list = new LinkedList<>();
        if (esResponse.getError() != null) {
            throw new Exception(esResponse.getError().getReason());
        }
        list.addAll(esResponse.getRows());
        return list;
    }

    // 从 SQL 查询响应解析字段元数据
    private List<TableField> fetchResultField4Sql(String response) throws Exception {
        List<TableField> fieldList = new ArrayList<>();
        EsResponse esResponse = new Gson().fromJson(response, EsResponse.class);
        if (esResponse.getError() != null) {
            throw new Exception(esResponse.getError().getReason());
        }

        for (EsResponse.Column column : esResponse.getColumns()) {
            TableField field = new TableField();
            field.setOriginName(column.getName());
            field.setOriginName(column.getName());
            field.setNativeType(column.getType());
            field.setType(column.getType().toUpperCase());
            field.setNativeType(field.getType());
            int fieldType = FieldUtils.resolveFieldType(field.getType());
            field.setExtractedFieldType(fieldType);
            field.setFieldType(fieldType);
            fieldList.add(field);
        }
        return fieldList;
    }

    // 从查询响应解析表列表
    private List<DatasetTableDTO> fetchTables(String response) throws Exception {
        List<DatasetTableDTO> tables = new ArrayList<>();
        EsResponse esResponse = new Gson().fromJson(response, EsResponse.class);
        if (esResponse.getError() != null) {
            throw new Exception(esResponse.getError().getReason());
        }

        for (String[] row : esResponse.getRows()) {

            DatasetTableDTO tableDesc = new DatasetTableDTO();
            if (row.length == 3 && row[1].contains("TABLE") && row[2].equalsIgnoreCase("INDEX")) {
                tableDesc.setTableName(row[0]);
            }
            if (row.length == 2 && row[1].contains("TABLE")) {
                tableDesc.setTableName(row[0]);
            }
            if (row.length == 4 && row[2].contains("TABLE") && row[3].equalsIgnoreCase("INDEX")) {
                tableDesc.setTableName(row[1]);
            }
            tableDesc.setType("es");
            tables.add(tableDesc);
        }
        return tables;
    }


    // 执行 Elasticsearch SQL POST 查询
    private String execQuery(DatasourceRequest datasourceRequest, String sql, String uri) {
        Es es = null;
        if (datasourceRequest.getDatasource() == null) {
            Collection<DatasourceSchemaDTO> datasourceSchemaDTOS = datasourceRequest.getDsList().values();
            es = JsonUtil.parseObject(datasourceSchemaDTOS.stream().findFirst().get().getConfiguration(), Es.class);
        } else {
            es = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Es.class);
        }

        uri = es.getUri() + uri;
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        if (StringUtils.isNotEmpty(es.getUsername()) && StringUtils.isNotEmpty(es.getPassword())) {
            String auth = es.getUsername() + ":" + es.getPassword();
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
            httpClientConfig.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodedAuth));
        }
        Request request = new Request();
        request.setQuery(sql);
        request.setFetch_size(datasourceRequest.getFetchSize());
        String url = es.getUrl().endsWith("/") ? es.getUrl() + uri : es.getUrl() + "/" + uri;
        return HttpClientUtil.post(url, new Gson().toJson(request), httpClientConfig);

    }

    // 执行 Elasticsearch 状态 GET 查询
    private String execGetQuery(DatasourceRequest datasourceRequest) {
        Es es = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Es.class);
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        if (StringUtils.isNotEmpty(es.getUsername()) && StringUtils.isNotEmpty(es.getPassword())) {
            String auth = es.getUsername() + ":" + es.getPassword();
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
            httpClientConfig.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodedAuth));
        }
        return HttpClientUtil.get(es.getUrl(), httpClientConfig);
    }


}
