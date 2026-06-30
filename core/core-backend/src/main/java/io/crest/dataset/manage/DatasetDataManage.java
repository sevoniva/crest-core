package io.crest.dataset.manage;

import io.crest.api.chart.dto.ChartSortFieldDTO;
import io.crest.api.dataset.dto.*;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.dataset.union.DatasetTableInfoDTO;
import io.crest.api.permissions.auth.dto.BusiPerCheckDTO;
import io.crest.api.permissions.dataset.api.RowPermissionsApi;
import io.crest.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.auth.bo.TokenUserBO;
import io.crest.chart.utils.ChartDataBuild;
import io.crest.commons.utils.SqlVariableHandleResult;
import io.crest.commons.utils.SqlParserUtils;
import io.crest.constant.AuthEnum;
import io.crest.constant.SQLConstants;
import io.crest.dataset.constant.DatasetTableType;
import io.crest.dataset.sync.DatasetSyncQueryManage;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.dataset.utils.FieldUtils;
import io.crest.dataset.utils.SqlUtils;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.manage.DataSourceManage;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.provider.ExcelUtils;
import io.crest.datasource.utils.DatasourceUtils;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.sql.SQLProvider;
import io.crest.engine.trans.*;
import io.crest.engine.utils.SQLUtils;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.CrestSqlFunctionTranslator;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.utils.SqlPlaceholderUtils;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.view.dto.ChartExtFilterDTO;
import io.crest.extensions.view.dto.ChartExtRequest;
import io.crest.extensions.view.dto.ColumnPermissionItem;
import io.crest.extensions.view.dto.SqlVariableDetails;
import io.crest.i18n.Translator;
import io.crest.portal.DataPortalPermissionManage;
import io.crest.system.manage.CorePermissionManage;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.utils.JsonUtil;
import io.crest.utils.TreeUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.crest.chart.manage.ChartDataManage.START_END_SEPARATOR;
import static io.crest.dataset.utils.TableUtils.format;

/**
 * 数据集数据读取服务，负责字段探测、预览查询、统计查询和筛选枚举值生成
 * 该类只组装并执行读取请求，不持久化数据集定义；字段权限、行权限和同步表路由会在查询前统一注入
 */
@Component
@SuppressWarnings("unchecked")
public class DatasetDataManage {
    @Resource
    private DatasetSQLManage datasetSQLManage;
    @Resource
    private DatasetSyncQueryManage datasetSyncQueryManage;
    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private PermissionManage permissionManage;
    @Resource
    private DatasetTableSqlLogManage datasetTableSqlLogManage;
    @Autowired(required = false)
    private PluginManageApi pluginManage;
    @Resource
    private CorePermissionManage corePermissionManage;
    @Autowired(required = false)
    private RowPermissionsApi rowPermissionsApi;
    @Resource
    private DataSourceManage dataSourceManage;
    @Resource
    private DataPortalPermissionManage dataPortalPermissionManage;

    private static Logger logger = LoggerFactory.getLogger(DatasetDataManage.class);

    /**
     * 返回可选的行权限服务，未启用权限模块时允许为空
     */
    private RowPermissionsApi getRowPermissionsApi() {
        return rowPermissionsApi;
    }

    /**
     * 不支持 FULL JOIN 的数据源类型列表，预览前需要提前阻断
     */
    public static final List<String> notFullDs = List.of("mysql", "obMysql", "mariadb", "Excel", "API", "H2", "h2");

    /**
     * 根据数据表配置读取字段元数据，兼容物理表、自定义 SQL、ES、Excel 和 API 同步表
     */
    public List<DatasetTableFieldDTO> getTableFields(DatasetTableDTO datasetTableDTO) throws Exception {
        List<TableField> tableFields = null;
        if (datasetTableDTO == null) {
            CrestException.throwException(Translator.get("i18n_table_id_can_not_empty"));
        }
        String type = StringUtils.defaultIfBlank(datasetTableDTO.getType(), DatasetTableType.DB);
        DatasetTableInfoDTO tableInfoDTO = resolveTableInfo(datasetTableDTO, type);
        if (Strings.CI.equals(type, DatasetTableType.DB) || Strings.CI.equals(type, DatasetTableType.SQL)) {
            CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(datasetTableDTO.getDatasourceId());
            if (coreDatasource == null) {
                CrestException.throwException(Translator.get("i18n_datasource_delete"));
            }
            if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name())
                    && Strings.CI.equals(type, DatasetTableType.DB)) {
                DatasourceRequest datasourceRequest = new DatasourceRequest();
                DatasourceDTO datasourceDTO = new DatasourceDTO();
                BeanUtils.copyBean(datasourceDTO, coreDatasource);
                datasourceRequest.setDatasource(datasourceDTO);
                datasourceRequest.setTable(tableInfoDTO.getTable());
                tableFields = ExcelUtils.getTableFields(datasourceRequest);
                return transFields(tableFields, true);
            }
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name()) || coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
                coreDatasource = engineManage.getEngineDatasource();
            }
            if (StringUtils.isNotEmpty(coreDatasource.getStatus()) && "Error".equalsIgnoreCase(coreDatasource.getStatus())) {
                CrestException.throwException(Translator.get("i18n_invalid_ds"));
            }
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            Provider provider = ProviderFactory.getProvider(coreDatasource.getType());

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            boolean isCross = Boolean.TRUE.equals(datasetTableDTO.getIsCross());
            datasourceRequest.setIsCross(isCross);
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql;
            if (Strings.CI.equals(type, DatasetTableType.DB)) {
                // 物理表探测只取元数据，LIMIT 0 可以避免读取真实业务数据
                sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableInfoDTO.getTable()) + " LIMIT 0 OFFSET 0";
                // 单源查询需要把内部 schema 别名转换为真实 schema 后再做方言转换
                Map map = JsonUtil.parseObject(datasourceSchemaDTO.getConfiguration(), Map.class);
                if (!datasourceRequest.getIsCross()) {
                    if (ObjectUtils.isNotEmpty(map.get("schema"))) {
                        sql = replaceSchemaAlias(sql, datasourceSchemaDTO.getSchemaAlias(), String.format(format, map.get("schema").toString()));
                    } else {
                        sql = removeSchemaAliasPrefix(sql, datasourceSchemaDTO.getSchemaAlias());
                    }
                    sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
                } else {
                    sql = removeSchemaAliasPrefix(sql, datasourceSchemaDTO.getSchemaAlias());
                    String tableSchema = datasetSQLManage.putObj2Map(datasourceRequest.getDsList(), datasetTableDTO, datasourceRequest.getIsCross());
                    sql = SqlUtils.addSchema(sql, tableSchema);
                }
            } else {
                // 自定义 SQL 先解析变量默认值，再以子查询方式探测字段结构
                String s = decodeSql(tableInfoDTO.getSql());
                SqlVariableHandleResult sqlResult = new SqlParserUtils().handleVariableDefaultValueWithPreparedParams(s, datasetTableDTO.getSqlVariableDetails(), true, false, null, isCross, datasourceRequest.getDsList(), pluginManage, getUserEntity());
                String originSql = sqlResult.getSql();
                datasourceRequest.setTableFieldWithValues(sqlResult.getTableFieldWithValues());
                originSql = provider.replaceComment(originSql);

                if (!datasourceRequest.getIsCross()) {
                    sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);
                    sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
                    // 方言转换完成后再替换占位符，避免原始 SQL 被错误改写
                    sql = provider.replaceTablePlaceHolder(sql, originSql);
                } else {
                    String tableSchema = datasetSQLManage.putObj2Map(datasourceRequest.getDsList(), datasetTableDTO, datasourceRequest.getIsCross());
                    sql = SqlUtils.addSchema(originSql, tableSchema);
                }
            }
            datasourceRequest.setQuery(sql.replaceAll("\r\n", " ").replaceAll("\n", " "));
            logger.debug("calcite data table field sql: " + datasourceRequest.getQuery());
            // DB 类型还需要传入原始表名，部分 provider 会用它补充字段原始名称
            if (Strings.CI.equals(type, DatasetTableType.DB)) {
                datasourceRequest.setTable(tableInfoDTO.getTable());
            }

            tableFields = provider.fetchTableField(datasourceRequest);
        } else if (Strings.CI.equals(type, DatasetTableType.Es)) {
            CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(datasetTableDTO.getDatasourceId());
            if (coreDatasource == null) {
                CrestException.throwException(Translator.get("i18n_datasource_delete"));
            }
            Provider provider = ProviderFactory.getProvider(type);
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceRequest.setDatasource(datasourceSchemaDTO);
            datasourceRequest.setTable(datasetTableDTO.getTableName());
            tableFields = provider.fetchTableField(datasourceRequest);
        } else {
            // Excel 和 API 最终落在引擎库，字段探测按同步后的物理表处理
            CoreDatasource sourceDatasource = dataSourceManage.getCoreDatasource(datasetTableDTO.getDatasourceId());
            if (sourceDatasource != null && sourceDatasource.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name())) {
                DatasourceRequest datasourceRequest = new DatasourceRequest();
                DatasourceDTO datasourceDTO = new DatasourceDTO();
                BeanUtils.copyBean(datasourceDTO, sourceDatasource);
                datasourceRequest.setDatasource(datasourceDTO);
                datasourceRequest.setTable(tableInfoDTO.getTable());
                tableFields = ExcelUtils.getTableFields(datasourceRequest);
                return transFields(tableFields, true);
            }
            CoreDatasource coreDatasource = engineManage.getEngineDatasource();
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            Provider provider = ProviderFactory.getDefaultProvider();

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableInfoDTO.getTable()) + " LIMIT 0 OFFSET 0";
            // 引擎库表使用默认 provider 做 schema 替换和方言转换
            sql = Utils.replaceSchemaAlias(sql, datasourceRequest.getDsList());
            sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
            datasourceRequest.setQuery(sql);
            logger.debug("calcite data table field sql: " + datasourceRequest.getQuery());
            tableFields = provider.fetchTableField(datasourceRequest);
        }
        return transFields(tableFields, true);
    }

    /**
     * 字段探测入口兼容历史调用方，物理表可只传 tableName，自定义 SQL 必须携带 sql。
     */
    private DatasetTableInfoDTO resolveTableInfo(DatasetTableDTO datasetTableDTO, String type) throws CrestException {
        DatasetTableInfoDTO tableInfoDTO = StringUtils.isBlank(datasetTableDTO.getInfo())
                ? new DatasetTableInfoDTO()
                : JsonUtil.parseObject(datasetTableDTO.getInfo(), DatasetTableInfoDTO.class);
        if (tableInfoDTO == null) {
            tableInfoDTO = new DatasetTableInfoDTO();
        }
        if (StringUtils.isBlank(tableInfoDTO.getTable()) && StringUtils.isNotBlank(datasetTableDTO.getTableName())) {
            tableInfoDTO.setTable(datasetTableDTO.getTableName());
        }
        if (Strings.CI.equals(type, DatasetTableType.SQL)) {
            if (StringUtils.isBlank(tableInfoDTO.getSql())) {
                CrestException.throwException(Translator.get("i18n_sql_not_empty"));
            }
            return tableInfoDTO;
        }
        if (!Strings.CI.equals(type, DatasetTableType.Es) && StringUtils.isBlank(tableInfoDTO.getTable())) {
            CrestException.throwException(Translator.get("i18n_table_id_can_not_empty"));
        }
        return tableInfoDTO;
    }

    /**
     * 将 provider 返回的字段元数据转换为数据集字段 DTO，并补齐字段类型和维度指标分组
     */
    public List<DatasetTableFieldDTO> transFields(List<TableField> tableFields, boolean defaultStatus) {
        return tableFields.stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            dto.setName(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : ele.getOriginName());
            dto.setOriginName(ele.getOriginName());
            dto.setDbFieldName(ele.getDbFieldName());
            if (StringUtils.isNotBlank(ele.getDbFieldName())) {
                dto.setEngineFieldName(ele.getDbFieldName());
                dto.setFieldShortName(ele.getDbFieldName());
            }
            dto.setChecked(defaultStatus);
            String type = StringUtils.firstNonBlank(ele.getType(), ele.getNativeType(), "TEXT");
            dto.setType(type);
            int fieldType = FieldUtils.resolveFieldType(type);
            dto.setExtractedFieldType(ObjectUtils.isEmpty(ele.getExtractedFieldType()) ? fieldType : ele.getExtractedFieldType());
            dto.setFieldType(ObjectUtils.isEmpty(ele.getFieldType()) ? fieldType : ele.getFieldType());
            dto.setGroupType(FieldUtils.resolveFieldGroup(dto.getFieldType()));
            dto.setExtField(0);
            dto.setDescription(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : null);
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 构造数据集预览查询，应用列权限、行权限、同步表路由和分页限制后返回字段与数据
     */
    public Map<String, Object> previewDataWithLimit(DatasetGroupInfoDTO datasetGroupInfoDTO, Integer start, Integer count, boolean checkPermission, boolean encode) throws Exception {
        if (encode) {
            DatasetUtils.dsDecode(datasetGroupInfoDTO);
        }

        // allFields 是外层 SELECT 的字段契约，空字段无法生成稳定预览结构
        List<DatasetTableFieldDTO> fields = datasetGroupInfoDTO.getAllFields();
        if (ObjectUtils.isEmpty(fields)) {
            CrestException.throwException(Translator.get("i18n_no_fields"));
        }

        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
        if (checkPermission || encode) {
            sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
        }
        String sql = (String) sqlMap.get("sql");

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        if (checkPermission) {
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                CrestException.throwException(Translator.get("i18n_no_column_permission"));
            }
        }
        buildFieldName(sqlMap, fields);
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        DatasourceUtils.checkDsStatus(dsMap);
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = datasetGroupInfoDTO.getIsCross();
        if (!crossDs) {
            if (notFullDs.contains(dsMap.entrySet().iterator().next().getValue().getType()) && (boolean) sqlMap.get("isFullJoin")) {
                CrestException.throwException(Translator.get("i18n_not_full"));
            }
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }
        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null && checkPermission) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }
        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.get(0));
        }

        // 以联合 SQL 为临时表，再叠加字段选择、行权限过滤和排序
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
        Field2SQLObj.field2sqlObj(sqlMeta, fields, fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
        String querySQL;
        if (start == null || count == null) {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, false);
        } else {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, start, count);
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.debug("calcite data preview sql: " + querySQL);

        // 预编译参数需要随查询请求传给 provider，避免自定义 SQL 参数丢失
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        applyPreparedParams(datasourceRequest, sqlMap);
        Map<String, Object> data = provider.fetchResultField(datasourceRequest);

        Map<String, Object> map = new LinkedHashMap<>();
        // provider 返回数组结构，这里按字段别名恢复为前端可读对象结构
        Map<String, Object> previewData = buildPreviewData(data, fields, desensitizationList, encode);
        map.put("data", previewData);
        if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getId())) {
            map.put("allFields", fields);
        } else {
            List<DatasetTableFieldDTO> fieldList = datasetTableFieldManage.selectByDatasetGroupId(datasetGroupInfoDTO.getId());
            if (encode) {
                DatasetUtils.listEncode(fieldList);
            }
            map.put("allFields", fieldList);
        }
        map.put("sql", Base64.getEncoder().encodeToString(querySQL.getBytes()));
        return map;
    }

    /**
     * 查询数据集总行数，目录节点或不存在的数据集返回 0
     */
    public Long datasetTotal(Long datasetGroupId) throws Exception {
        DatasetGroupInfoDTO dto = datasetGroupManage.getForCount(datasetGroupId);
        if (ObjectUtils.isEmpty(dto)) return 0L;
        if (Strings.CI.equals(dto.getNodeType(), "dataset")) {
            return datasetTotal(dto, null, new ChartExtRequest());
        }
        return 0L;
    }

    /**
     * 查询带当前行权限过滤的数据集行数，异常时返回空值供调用方降级处理
     */
    public Long datasetCountWithWhere(Long datasetGroupId) {
        try {
            DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.getForCount(datasetGroupId);
            Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
            sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
            String sql = (String) sqlMap.get("sql");

            // 计数前仍需准备字段别名，行权限和排序构造依赖完整字段列表
            List<DatasetTableFieldDTO> fields = datasetGroupInfoDTO.getAllFields();
            if (ObjectUtils.isEmpty(fields)) {
                CrestException.throwException(Translator.get("i18n_no_fields"));
            }

            buildFieldName(sqlMap, fields);

            Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            DatasourceUtils.checkDsStatus(dsMap);
            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean crossDs = datasetGroupInfoDTO.getIsCross();
            if (!crossDs) {
                if (notFullDs.contains(dsMap.entrySet().iterator().next().getValue().getType()) && (boolean) sqlMap.get("isFullJoin")) {
                    CrestException.throwException(Translator.get("i18n_not_full"));
                }
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
            TokenUserBO user = AuthUtils.getUser();
            if (user != null) {
                rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
            }

            Provider provider;
            if (crossDs) {
                provider = ProviderFactory.getDefaultProvider();
            } else {
                provider = ProviderFactory.getProvider(dsList.get(0));
            }

            // 先构造权限过滤后的明细 SQL，再交给通用计数入口包装 COUNT
            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            Field2SQLObj.field2sqlObj(sqlMeta, fields, fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
            Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), fields, crossDs, dsMap, Utils.getParams(fields), null, pluginManage);
            String replaceSql = provider.rebuildSQL(SQLProvider.createQuerySQL(sqlMeta, false, false, false), sqlMeta, crossDs, dsMap);
            return datasetTotal(datasetGroupInfoDTO, replaceSql, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对给定数据集 SQL 包装 COUNT 查询，可复用外部已加过滤的 SQL
     */
    public Long datasetTotal(DatasetGroupInfoDTO datasetGroupInfoDTO, String s, ChartExtRequest request) throws Exception {
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, request);
        sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = datasetGroupInfoDTO.getIsCross();
        String sql;
        if (StringUtils.isEmpty(s)) {
            sql = (String) sqlMap.get("sql");
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }
        } else {
            sql = s;
        }
        String querySQL = "SELECT COUNT(*) FROM (" + sql + ") t_a_0";
        logger.debug("calcite data count sql: " + querySQL);

        // COUNT 查询同样需要携带自定义 SQL 参数的预编译值
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        applyPreparedParams(datasourceRequest, sqlMap);

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsMap.entrySet().iterator().next().getValue().getType());
        }
        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        if (ObjectUtils.isNotEmpty(dataList) && ObjectUtils.isNotEmpty(dataList.get(0)) && ObjectUtils.isNotEmpty(dataList.get(0)[0])) {
            return Long.valueOf(dataList.get(0)[0]);
        }
        return 0L;
    }

    /**
     * 预览自定义 SQL 并记录执行日志，日志只在带表 ID 的编辑场景写入
     */
    public Map<String, Object> previewSqlWithLog(PreviewSqlDTO dto) {
        if (dto == null) {
            return null;
        }
        SqlLogDTO sqlLogDTO = new SqlLogDTO();
        String sql = decodeSql(dto.getSql());
        sqlLogDTO.setSql(sql);
        Map<String, Object> map = null;
        try {
            sqlLogDTO.setStartTime(System.currentTimeMillis());
            map = previewSql(dto);
            sqlLogDTO.setEndTime(System.currentTimeMillis());
            sqlLogDTO.setSpend(sqlLogDTO.getEndTime() - sqlLogDTO.getStartTime());
            sqlLogDTO.setStatus("Completed");
        } catch (Exception e) {
            sqlLogDTO.setStatus("Error");
            CrestException.throwException(e.getMessage());
        } finally {
            if (ObjectUtils.isNotEmpty(dto.getTableId())) {
                sqlLogDTO.setTableId(dto.getTableId());
                datasetTableSqlLogManage.save(sqlLogDTO);
            }
        }
        return map;
    }

    /**
     * 把 SQL 变量解析出的预编译参数复制到数据源请求，避免 provider 修改共享对象
     */
    private void applyPreparedParams(DatasourceRequest datasourceRequest, Map<String, Object> sqlMap) {
        if (sqlMap == null) {
            return;
        }
        List<TableFieldWithValue> tableFieldWithValues = (List<TableFieldWithValue>) sqlMap.get("tableFieldWithValues");
        if (CollectionUtils.isEmpty(tableFieldWithValues)) {
            return;
        }
        datasourceRequest.setTableFieldWithValues(tableFieldWithValues.stream().map(TableFieldWithValue::copy).toList());
    }

    /**
     * 前端按 Base64 传输自定义 SQL，后端统一校验编码格式并返回业务错误。
     */
    private String decodeSql(String encodedSql) {
        if (StringUtils.isBlank(encodedSql)) {
            CrestException.throwException(Translator.get("i18n_sql_not_empty"));
        }
        try {
            return new String(Base64.getDecoder().decode(encodedSql), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            CrestException.throwException(Translator.get("i18n_sql_encoding_invalid"));
            return null;
        }
    }

    /**
     * 返回当前用户信息，SQL 变量默认值解析会用它计算用户相关变量
     */
    private UserFormVO getUserEntity() {
        if (getRowPermissionsApi() == null) {
            return null;
        }
        return getRowPermissionsApi().getUserById(AuthUtils.getUser().getUserId());
    }

    /**
     * 预览单段自定义 SQL，负责变量解析、方言转换、分页包装和结果字段还原
     */
    public Map<String, Object> previewSql(PreviewSqlDTO dto) throws CrestException {
        CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(dto.getDatasourceId());
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) || coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name())) {
            BeanUtils.copyBean(datasourceSchemaDTO, engineManage.getEngineDatasource());
        } else {
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
        }

        if (StringUtils.isNotEmpty(datasourceSchemaDTO.getStatus()) && "Error".equalsIgnoreCase(datasourceSchemaDTO.getStatus())) {
            CrestException.throwException(Translator.get("i18n_invalid_ds"));
        }

        String alias = String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId());
        datasourceSchemaDTO.setSchemaAlias(alias);

        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(datasourceSchemaDTO.getId(), datasourceSchemaDTO);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        boolean isCross = Boolean.TRUE.equals(dto.getIsCross());
        datasourceRequest.setIsCross(isCross);
        Provider provider = ProviderFactory.getProvider(datasourceSchemaDTO.getType());

        // 先解析 SQL 参数并替换默认值，跨源时还要保留预编译参数上下文
        String s = decodeSql(dto.getSql());
        SqlVariableHandleResult sqlResult = new SqlParserUtils().handleVariableDefaultValueWithPreparedParams(datasetSQLManage.subPrefixSuffixChar(s), dto.getSqlVariableDetails(), true, true, null, isCross, dsMap, pluginManage, getUserEntity());
        String originSql = sqlResult.getSql();
        datasourceRequest.setTableFieldWithValues(sqlResult.getTableFieldWithValues());
        originSql = provider.replaceComment(originSql);

        // 将自定义 SQL 包装为临时表，再在外层追加分页限制。
        String sql;
        if (isCross) {
            DatasetTableDTO currentDs = new DatasetTableDTO();
            BeanUtils.copyBean(currentDs, dto);
            currentDs.setType("sql");
            String tableSchema = datasetSQLManage.putObj2Map(dsMap, currentDs, isCross);
            sql = SqlUtils.addSchema(originSql, tableSchema);
            sql = translatePreviewSql(sql, datasourceSchemaDTO);
            if (Utils.isNeedOrder(List.of(datasourceSchemaDTO.getType()))) {
                // 需要稳定排序的数据源先探测首个字段，再用它构造预览排序
                String sqlField = SQLUtils.buildOriginPreviewSql(sql, 0, 0);
                sqlField = translatePreviewSql(sqlField, datasourceSchemaDTO);
                datasourceRequest.setQuery(sqlField);

                List<TableField> list = provider.fetchTableField(datasourceRequest);
                if (ObjectUtils.isEmpty(list)) {
                    return null;
                }
                sql = SQLUtils.buildOriginPreviewSqlWithOrderBy(sql, 100, 0, String.format(SQLConstants.FIELD_DOT_FIX, list.get(0).getOriginName()) + " ASC ");
            } else {
                sql = SQLUtils.buildOriginPreviewSql(sql, 100, 0);
            }
        } else {
            if (Utils.isNeedOrder(List.of(datasourceSchemaDTO.getType()))) {
                // 单源 SQL 先通过占位符包装探测字段，避免直接拼接影响方言转换
                String sqlField = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);

                sqlField = provider.transSqlDialect(sqlField, datasourceRequest.getDsList());
                // 方言转换完成后再替换为原始 SQL，并执行函数翻译
                sqlField = replacePreviewTablePlaceholderAndTranslate(provider, sqlField, originSql, datasourceSchemaDTO);
                datasourceRequest.setQuery(sqlField);

                List<TableField> list = provider.fetchTableField(datasourceRequest);
                if (ObjectUtils.isEmpty(list)) {
                    return null;
                }
                sql = SQLUtils.buildOriginPreviewSqlWithOrderBy(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0, String.format(SQLConstants.FIELD_DOT_FIX, list.get(0).getOriginName()) + " ASC ");
            } else {
                sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0);
            }
            sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
            // 最终预览 SQL 使用同一占位符替换路径，保证函数翻译规则一致
            sql = replacePreviewTablePlaceholderAndTranslate(provider, sql, originSql, datasourceSchemaDTO);
        }

        logger.debug("calcite data preview sql: " + sql);
        datasourceRequest.setQuery(sql);
        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        // 自定义 SQL 的字段来自 provider 探测结果，需转回数据集字段 DTO
        List<TableField> fList = (List<TableField>) data.get("fields");
        List<DatasetTableFieldDTO> fields = transFields(fList, false);
        Map<String, Object> previewData = buildPreviewData(data, fields, new HashMap<>(), false);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", previewData);
        map.put("sql", Base64.getEncoder().encodeToString(sql.getBytes()));
        return map;
    }

    /**
     * 替换预览 SQL 的表占位符，并执行数据源函数翻译
     */
    String replacePreviewTablePlaceholderAndTranslate(Provider provider, String sql, String originSql, DatasourceSchemaDTO datasource) {
        return translatePreviewSql(provider.replaceTablePlaceHolder(sql, originSql), datasource);
    }

    /**
     * 按数据源类型翻译自定义 SQL 中的平台函数
     */
    private String translatePreviewSql(String sql, DatasourceSchemaDTO datasource) {
        return CrestSqlFunctionTranslator.translate(sql, datasource);
    }

    /**
     * 将 provider 的二维数组结果转为字段名对象列表，并按列权限规则执行脱敏
     */
    public Map<String, Object> buildPreviewData(Map<String, Object> data, List<DatasetTableFieldDTO> fields, Map<String, ColumnPermissionItem> desensitizationList, boolean isEncode) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String[]> dataList = (List<String[]>) data.get("data");
        List<LinkedHashMap<String, Object>> dataObjectList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(dataList)) {
            for (int i = 0; i < dataList.size(); i++) {
                String[] row = dataList.get(i);
                LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
                if (row.length > 0) {
                    for (int j = 0; j < fields.size(); j++) {
                        String res = row[j];
                        // 小数值统一展开科学计数法，避免前端枚举和预览展示不一致
                        if (fields.get(j).getFieldType() == 3 && Strings.CI.contains(res, "E")) {
                            BigDecimal bigDecimal = new BigDecimal(res);
                            res = String.format("%.8f", bigDecimal);
                        }
                        if (desensitizationList.keySet().contains(fields.get(j).getEngineFieldName())) {
                            obj.put(fields.get(j).getEngineFieldName(), ChartDataBuild.desensitizationValue(desensitizationList.get(fields.get(j).getEngineFieldName()), String.valueOf(res)));
                        } else {
                            obj.put(ObjectUtils.isNotEmpty(fields.get(j).getEngineFieldName()) ? fields.get(j).getEngineFieldName() : fields.get(j).getOriginName(), res);
                        }
                    }
                }
                dataObjectList.add(obj);
            }
        }

        if (isEncode) {
            DatasetUtils.listEncode(fields);
        }

        map.put("fields", fields);
        map.put("data", dataObjectList);
        return map;
    }

    /**
     * 为字段列表补齐引擎字段名，新增字段沿用联合 SQL 的字段别名规则
     */
    public void buildFieldName(Map<String, Object> sqlMap, List<DatasetTableFieldDTO> fields) {
        // 联合 SQL 返回的字段列表是普通字段别名的权威来源
        List<DatasetTableFieldDTO> unionFields = (List<DatasetTableFieldDTO>) sqlMap.get("field");
        for (DatasetTableFieldDTO datasetTableFieldDTO : fields) {
            DatasetTableFieldDTO dto = datasetTableFieldManage.selectById(datasetTableFieldDTO.getId());
            if (ObjectUtils.isEmpty(dto)) {
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                    for (DatasetTableFieldDTO fieldDTO : unionFields) {
                        if (Objects.equals(datasetTableFieldDTO.getDatasetTableId(), fieldDTO.getDatasetTableId()) && Objects.equals(datasetTableFieldDTO.getOriginName(), fieldDTO.getOriginName())) {
                            datasetTableFieldDTO.setEngineFieldName(fieldDTO.getEngineFieldName());
                            datasetTableFieldDTO.setFieldShortName(fieldDTO.getFieldShortName());
                        }
                    }
                }
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_CALC)) {
                    String engineFieldName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                    datasetTableFieldDTO.setEngineFieldName(engineFieldName);
                    datasetTableFieldDTO.setFieldShortName(engineFieldName);
                    datasetTableFieldDTO.setExtractedFieldType(datasetTableFieldDTO.getFieldType());
                }
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_GROUP)) {
                    String engineFieldName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                    datasetTableFieldDTO.setEngineFieldName(engineFieldName);
                    datasetTableFieldDTO.setFieldShortName(engineFieldName);
                    datasetTableFieldDTO.setExtractedFieldType(0);
                    datasetTableFieldDTO.setFieldType(0);
                    datasetTableFieldDTO.setGroupType("d");
                }
            } else {
                datasetTableFieldDTO.setEngineFieldName(dto.getEngineFieldName());
                datasetTableFieldDTO.setFieldShortName(dto.getFieldShortName());
            }
        }
    }

    /**
     * 根据编辑态数据集和字段配置查询单字段枚举值，不额外应用列权限过滤
     */
    public List<String> getFieldEnumDs(EnumObj map) throws Exception {
        DatasetTableFieldDTO field = map.getField();
        DatasetGroupInfoDTO datasetGroupInfoDTO = map.getDataset();
        if (field == null) {
            CrestException.throwException(Translator.get("i18n_no_field"));
        }
        List<DatasetTableFieldDTO> allFields = new ArrayList<>();

        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
        sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
        String sql = (String) sqlMap.get("sql");

        allFields.addAll(datasetGroupInfoDTO.getAllFields());

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = datasetGroupInfoDTO.getIsCross();
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        // 枚举查询只选择目标字段，并在 provider 支持时使用 DISTINCT 降低返回量
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

        // 查询字段需要先补齐引擎字段名，后续 SQL 生成依赖别名
        List<DatasetTableFieldDTO> fields = Collections.singletonList(field);
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.get(0));
        }

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        WhereTree2Str.transFilterTrees(sqlMeta, null, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        String querySQL;
        querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, !Strings.CI.equals(dsType, "es"));
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.debug("calcite data enum sql: " + querySQL);

        // 枚举值查询复用 provider 读取路径，并携带自定义 SQL 参数值
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        applyPreparedParams(datasourceRequest, sqlMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        dataList = dataList.stream().filter(row -> {
            boolean hasEmpty = false;
            for (String s : row) {
                if (StringUtils.isBlank(s)) {
                    hasEmpty = true;
                    break;
                }
            }
            return !hasEmpty;
        }).toList();
        List<String> previewData = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(dataList)) {
            List<String> tmpData = dataList.stream().map(ele -> (ObjectUtils.isNotEmpty(ele) && ele.length > 0) ? ele[0] : null).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(tmpData)) {
                for (int i = 0; i < tmpData.size(); i++) {
                    String val = tmpData.get(i);
                    if (field.getFieldType() == 3 && Strings.CI.contains(val, "E")) {
                        BigDecimal bigDecimal = new BigDecimal(val);
                        val = String.format("%.8f", bigDecimal);
                        tmpData.set(i, val);
                    }
                }
                previewData = tmpData;
            }
        }
        return previewData;
    }

    /**
     * 查询多个字段的枚举值并合并去重，适用于查询组件的备选项加载
     */
    public List<String> getFieldEnum(MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        if (CollectionUtils.isEmpty(multFieldValuesRequest.getFieldIds())) {
            return Collections.emptyList();
        }
        // 每个字段独立生成枚举 SQL，最后按出现顺序合并去重
        List<List<String>> list = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(multFieldValuesRequest.getFieldIds())) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                CrestException.throwException(Translator.get("i18n_no_field"));
            }
            List<DatasetTableFieldDTO> allFields = new ArrayList<>();
            // 图表计算字段会参与表达式解析，需要并入可引用字段列表
            Long datasetGroupId = field.getDatasetGroupId();

            // 查询组件可能来自门户发布页，门户授权命中时允许读取对应数据集
            BusiPerCheckDTO dto = new BusiPerCheckDTO();
            dto.setId(datasetGroupId);
            dto.setAuthEnum(AuthEnum.READ);
            boolean portalVisualizationRead = dataPortalPermissionManage
                    .canReadPublishedVisualizationDataset(multFieldValuesRequest.getVisualizationId(), datasetGroupId);
            boolean checked = portalVisualizationRead || corePermissionManage.checkAuth(dto);
            if (!checked) {
                CrestException.throwException(Translator.get("i18n_no_dataset_permission"));
            }
            if (field.getChartId() != null) {
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            }
            DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTOForVisualizationRead(
                    datasetGroupId, null, multFieldValuesRequest.getVisualizationId());

            Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            boolean crossDs = datasetGroupInfoDTO.getIsCross();
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // 枚举 SQL 以数据集 SQL 为临时表，并叠加行权限过滤
            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

            // 字段权限会返回脱敏规则，后续结果值需要按同一规则处理
            List<DatasetTableFieldDTO> fields = Collections.singletonList(field);
            Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                CrestException.throwException(Translator.get("i18n_no_column_permission"));
            }
            buildFieldName(sqlMap, fields);

            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean needOrder = Utils.isNeedOrder(dsList);

            List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
            TokenUserBO user = AuthUtils.getUser();
            if (user != null) {
                rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
            }

            Provider provider;
            if (crossDs) {
                provider = ProviderFactory.getDefaultProvider();
            } else {
                provider = ProviderFactory.getProvider(dsList.get(0));
            }

            String dsType = null;
            if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
                Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
                dsType = next.getValue().getType();
            }

            Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            String querySQL;
            if (multFieldValuesRequest.getResultMode() == 0) {
                querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, !Strings.CI.equals(dsType, "es"), 0, 1000);
            } else {
                querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, !Strings.CI.equals(dsType, "es"));
            }
            querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
            logger.debug("calcite data enum sql: " + querySQL);

            // 枚举查询同样要携带预编译参数，保障自定义 SQL 过滤条件生效
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setQuery(querySQL);
            datasourceRequest.setDsList(dsMap);
            datasourceRequest.setIsCross(crossDs);
            applyPreparedParams(datasourceRequest, sqlMap);

            Map<String, Object> data = provider.fetchResultField(datasourceRequest);
            List<String[]> dataList = (List<String[]>) data.get("data");
            dataList = dataList.stream().filter(row -> {
                boolean hasEmpty = false;
                for (String s : row) {
                    if (StringUtils.isBlank(s)) {
                        hasEmpty = true;
                        break;
                    }
                }
                return !hasEmpty;
            }).toList();
            List<String> previewData = new ArrayList<>();
            if (ObjectUtils.isNotEmpty(dataList)) {
                List<String> tmpData = dataList.stream().map(ele -> (ObjectUtils.isNotEmpty(ele) && ele.length > 0) ? ele[0] : null).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(tmpData)) {
                    for (int i = 0; i < tmpData.size(); i++) {
                        String val = tmpData.get(i);
                        if (field.getFieldType() == 3 && Strings.CI.contains(val, "E")) {
                            BigDecimal bigDecimal = new BigDecimal(val);
                            val = String.format("%.8f", bigDecimal);
                            tmpData.set(i, val);
                        }
                    }
                    if (desensitizationList.keySet().contains(field.getEngineFieldName())) {
                        for (int i = 0; i < tmpData.size(); i++) {
                            previewData.add(ChartDataBuild.desensitizationValue(desensitizationList.get(field.getEngineFieldName()), tmpData.get(i)));
                        }
                    } else {
                        previewData = tmpData;
                    }
                }
                list.add(previewData);
            }
        }

        // 多字段枚举合并为单一列表，LinkedHashSet 保留原始查询顺序
        Set<String> result = new LinkedHashSet<>();
        for (List<String> l : list) {
            result.addAll(l);
        }
        return result.stream().toList();
    }

    /**
     * 查询联动组件的键值对象枚举，支持查询字段、展示字段、排序字段和组件过滤条件
     */
    public List<Map<String, Object>> getFieldEnumObj(EnumValueRequest request) throws Exception {
        List<Long> ids = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(request.getQueryId())) {
            ids.add(request.getQueryId());
        }
        if (ObjectUtils.isNotEmpty(request.getDisplayId())) {
            ids.add(request.getDisplayId());
        }

        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        if (ids.size() == 2 && Objects.equals(ids.get(0), ids.get(1))) {
            ids.remove(1);
        }

        SQLMeta sqlMeta = new SQLMeta();
        DatasetGroupInfoDTO datasetGroupInfoDTO = null;
        List<DatasetTableFieldDTO> fields = new ArrayList<>();
        Map<String, Object> sqlMap = null;
        boolean crossDs = false;
        Map<Long, DatasourceSchemaDTO> dsMap = null;

        if (ObjectUtils.isNotEmpty(request.getSortId())) {
            // 排序字段不在查询或展示字段内时，也要加入 SELECT 才能参与排序
            if (!request.getSortId().equals(request.getQueryId()) && !request.getSortId().equals(request.getDisplayId())) {
                ids.add(request.getSortId());
            }
        }

        List<DatasetTableFieldDTO> allFields = new ArrayList<>();

        for (Long id : ids) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                CrestException.throwException(Translator.get("i18n_no_field"));
            }

            // 每个请求字段都必须来自同一个数据集，后续 SQLMeta 会累积字段表达式
            Long datasetGroupId = field.getDatasetGroupId();

            // 发布门户读取和普通数据集读取共用同一权限入口
            BusiPerCheckDTO dto = new BusiPerCheckDTO();
            dto.setId(datasetGroupId);
            dto.setAuthEnum(AuthEnum.READ);
            boolean portalVisualizationRead = dataPortalPermissionManage
                    .canReadPublishedVisualizationDataset(request.getVisualizationId(), datasetGroupId);
            boolean checked = portalVisualizationRead || corePermissionManage.checkAuth(dto);
            if (!checked) {
                CrestException.throwException(Translator.get("i18n_no_dataset_permission"));
            }

            if (field.getChartId() != null) {
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            }
            datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTOForVisualizationRead(
                    datasetGroupId, null, request.getVisualizationId());

            sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            crossDs = datasetGroupInfoDTO.getIsCross();
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // 所有字段共享同一个数据集 SQL 临时表
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            fields.add(field);
        }

        // 列权限过滤可能删除字段，也可能要求对返回值脱敏
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
        if (ObjectUtils.isEmpty(fields)) {
            CrestException.throwException(Translator.get("i18n_no_column_permission"));
        }
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        // 组件过滤条件只保留当前数据集内的字段，SQL 参数型过滤交由变量解析阶段处理
        List<ChartExtFilterDTO> extFilterList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(request.getFilter())) {
            for (ChartExtFilterDTO filterDTO : request.getFilter()) {
                // fieldId 可能是逗号分隔的多个字段，树形过滤和普通过滤分别展开
                String fieldId = filterDTO.getFieldId();
                if (filterDTO.getIsTree() == null) {
                    filterDTO.setIsTree(false);
                }

                boolean hasParameters = false;
                List<SqlVariableDetails> sqlVariables = datasetGroupManage.sqlParams(Arrays.asList(datasetGroupInfoDTO.getId()));
                if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(sqlVariables)) {
                    for (SqlVariableDetails parameter : Optional.ofNullable(filterDTO.getParameters()).orElse(new ArrayList<>())) {
                        String parameterId = Strings.CS.endsWith(parameter.getId(), START_END_SEPARATOR) ? parameter.getId().split(START_END_SEPARATOR)[0] : parameter.getId();
                        if (sqlVariables.stream().map(SqlVariableDetails::getId).collect(Collectors.toList()).contains(parameterId)) {
                            hasParameters = true;
                        }
                    }
                }

                if (hasParameters) {
                    continue;
                }

                if (StringUtils.isNotEmpty(fieldId)) {
                    List<Long> fieldIds = Arrays.stream(fieldId.split(",")).map(Long::valueOf).collect(Collectors.toList());

                    if (filterDTO.getIsTree()) {
                        ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                        BeanUtils.copyBean(filterRequest, filterDTO);
                        filterRequest.setDatasetTableFieldList(new ArrayList<>());
                        for (Long fId : fieldIds) {
                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                filterRequest.getDatasetTableFieldList().add(datasetTableField);
                            }
                        }
                        if (ObjectUtils.isNotEmpty(filterRequest.getDatasetTableFieldList())) {
                            extFilterList.add(filterRequest);
                        }
                    } else {
                        for (Long fId : fieldIds) {
                            ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                            BeanUtils.copyBean(filterRequest, filterDTO);
                            filterRequest.setFieldId(fId + "");

                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            filterRequest.setDatasetTableField(datasetTableField);
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                extFilterList.add(filterRequest);
                            }
                        }
                    }
                }
            }
        }

        // 搜索词作为展示字段的 like 条件追加，用于远程搜索备选项
        if (StringUtils.isNotEmpty(request.getSearchText())) {
            ChartExtFilterDTO dto = new ChartExtFilterDTO();
            DatasetTableFieldDTO field = null;
            if (ids.size() == 1) {
                field = datasetTableFieldManage.selectById(ids.get(0));
            } else {
                field = datasetTableFieldManage.selectById(ids.get(1));
            }
            dto.setDatasetTableField(field);
            dto.setFieldId(field.getId() + "");
            dto.setIsTree(false);
            dto.setOperator("like");
            dto.setValue(List.of(request.getSearchText()));
            extFilterList.add(dto);
        }

        // 指定排序字段时不再对单字段结果强制 DISTINCT，避免排序字段被折叠
        boolean sortDistinct = true;
        if (ObjectUtils.isNotEmpty(request.getSortId())) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(request.getSortId());
            if (field == null) {
                CrestException.throwException(Translator.get("i18n_no_field"));
            }
            ChartSortFieldDTO sortField = new ChartSortFieldDTO();
            BeanUtils.copyBean(sortField, field);
            sortField.setOrderDirection(request.getSort().equalsIgnoreCase("asc") ? "asc" : "desc");
            datasetGroupInfoDTO.setSortFields(Collections.singletonList(sortField));
            sortDistinct = false;
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.get(0));
        }

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        ExtWhere2Str.extWhere2sqlOjb(sqlMeta, extFilterList, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        String querySQL;
        if (request.getResultMode() == 0) {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, sortDistinct && ids.size() == 1 && !Strings.CI.equals(dsType, "es"), 0, 1000);
        } else {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, sortDistinct && ids.size() == 1 && !Strings.CI.equals(dsType, "es"));
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.debug("calcite data enum sql: " + querySQL);

        // 联动枚举请求同样走统一 provider，跨源时由默认 provider 分发
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        applyPreparedParams(datasourceRequest, sqlMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        dataList = dataList.stream().filter(row -> {
            boolean hasEmpty = false;
            for (String s : row) {
                if (StringUtils.isBlank(s)) {
                    hasEmpty = true;
                    break;
                }
            }
            return !hasEmpty;
        }).toList();
        Map<String, String[]> distinctData = new LinkedHashMap<>();
        for (String[] arr : dataList) {
            String key = Arrays.toString(arr);
            if (!distinctData.containsKey(key)) {
                distinctData.put(key, arr);
            }
        }

        List<String[]> distinctDataList = new ArrayList<>();
        for (Map.Entry<String, String[]> ele : distinctData.entrySet()) {
            distinctDataList.add(ele.getValue());
        }

        List<Map<String, Object>> previewData = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(distinctDataList)) {
            for (String[] ele : distinctDataList) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < fields.size(); i++) {
                    String val = ele[i];
                    DatasetTableFieldDTO field = fields.get(i);
                    if (field.getFieldType() == 3 && Strings.CI.contains(val, "E")) {
                        BigDecimal bigDecimal = new BigDecimal(val);
                        val = String.format("%.8f", bigDecimal);
                    }
                    if (desensitizationList.containsKey(field.getEngineFieldName())) {
                        String str = ChartDataBuild.desensitizationValue(desensitizationList.get(field.getEngineFieldName()), val);
                        map.put(field.getId() + "", str);
                    } else {
                        map.put(field.getId() + "", val);
                    }
                }
                previewData.add(map);
            }
        }
        return previewData;
    }

    /**
     * 查询多级树形筛选值，按字段顺序构建层级节点并合并重复路径
     */
    public List<BaseTreeNodeDTO> getFieldValueTree(MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        List<Long> ids = multFieldValuesRequest.getFieldIds();
        if (ids.isEmpty()) {
            CrestException.throwException("no field selected.");
        }
        // 多级树要求每一列代表一层，后续会按行逐层生成节点
        List<List<String>> list = new ArrayList<>();
        List<DatasetTableFieldDTO> fields = new ArrayList<>();

        // 以第一个字段确定数据集，再加载图表计算字段作为表达式解析上下文
        List<DatasetTableFieldDTO> allFields = new ArrayList<>();
        DatasetTableFieldDTO field = datasetTableFieldManage.selectById(ids.get(0));
        Long datasetGroupId = field.getDatasetGroupId();
        if (field.getChartId() != null) {
            allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
        }
        DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTOForVisualizationRead(
                datasetGroupId, null, multFieldValuesRequest.getVisualizationId());

        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
        sqlMap = datasetSyncQueryManage.routeIfSynced(datasetGroupInfoDTO, sqlMap);
        String sql = (String) sqlMap.get("sql");

        allFields.addAll(datasetGroupInfoDTO.getAllFields());

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = datasetGroupInfoDTO.getIsCross();
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        // 树形值查询仍以数据集 SQL 为临时表，并按字段顺序选择层级列
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

        for (Long id : ids) {
            DatasetTableFieldDTO f = datasetTableFieldManage.selectById(id);
            if (f == null) {
                CrestException.throwException(Translator.get("i18n_no_field"));
            }
            fields.add(f);
        }

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
        if (ObjectUtils.isEmpty(fields)) {
            CrestException.throwException(Translator.get("i18n_no_column_permission"));
        }
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.get(0));
        }

        // 外部组件过滤会裁剪树形值，参数型过滤跳过以避免重复绑定 SQL 变量
        List<ChartExtFilterDTO> extFilterList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(multFieldValuesRequest.getFilter())) {
            for (ChartExtFilterDTO filterDTO : multFieldValuesRequest.getFilter()) {
                // fieldId 可能包含多个字段，树形过滤保留字段列表，普通过滤拆成单字段条件
                String fieldId = filterDTO.getFieldId();
                if (filterDTO.getIsTree() == null) {
                    filterDTO.setIsTree(false);
                }

                boolean hasParameters = false;
                List<SqlVariableDetails> sqlVariables = datasetGroupManage.sqlParamsForVisualizationRead(
                        Arrays.asList(datasetGroupInfoDTO.getId()), multFieldValuesRequest.getVisualizationId());
                if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(sqlVariables)) {
                    for (SqlVariableDetails parameter : Optional.ofNullable(filterDTO.getParameters()).orElse(new ArrayList<>())) {
                        String parameterId = Strings.CS.endsWith(parameter.getId(), START_END_SEPARATOR) ? parameter.getId().split(START_END_SEPARATOR)[0] : parameter.getId();
                        if (sqlVariables.stream().map(SqlVariableDetails::getId).collect(Collectors.toList()).contains(parameterId)) {
                            hasParameters = true;
                        }
                    }
                }

                if (hasParameters) {
                    continue;
                }

                if (StringUtils.isNotEmpty(fieldId)) {
                    List<Long> fieldIds = Arrays.stream(fieldId.split(",")).map(Long::valueOf).collect(Collectors.toList());

                    if (filterDTO.getIsTree()) {
                        ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                        BeanUtils.copyBean(filterRequest, filterDTO);
                        filterRequest.setDatasetTableFieldList(new ArrayList<>());
                        for (Long fId : fieldIds) {
                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                filterRequest.getDatasetTableFieldList().add(datasetTableField);
                            }
                        }
                        if (ObjectUtils.isNotEmpty(filterRequest.getDatasetTableFieldList())) {
                            extFilterList.add(filterRequest);
                        }
                    } else {
                        for (Long fId : fieldIds) {
                            ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                            BeanUtils.copyBean(filterRequest, filterDTO);
                            filterRequest.setFieldId(fId + "");

                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            filterRequest.setDatasetTableField(datasetTableField);
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                extFilterList.add(filterRequest);
                            }
                        }
                    }
                }
            }
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        ExtWhere2Str.extWhere2sqlOjb(sqlMeta, extFilterList, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
        String querySQL;
        if (multFieldValuesRequest.getResultMode() == 0) {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, 0, 1000);
        } else {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, false);
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.debug("filter tree sql: " + querySQL);

        // 树形枚举请求携带预编译参数，保持自定义 SQL 参数过滤一致
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        applyPreparedParams(datasourceRequest, sqlMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> rows = (List<String[]>) data.get("data");

        // 空值只能出现在路径尾部，中间层为空后又出现非空值的行会破坏树结构
        Set<String> pkSet = new HashSet<>();
        rows = rows.stream().filter(row -> {
            boolean hasEmpty = false;
            int emptyCount = 0;
            for (String s : row) {
                if (StringUtils.isBlank(s)) {
                    emptyCount++;
                    // 标记已遇到第一个空层级
                    hasEmpty = true;
                } else if (hasEmpty) {
                    // 空层级之后又出现非空层级，说明路径不连续
                    return false;
                }
            }
            return emptyCount != row.length;
        }).toList();
        List<BaseTreeNodeDTO> treeNodes = rows.stream().map(row -> buildTreeNode(row, pkSet)).flatMap(Collection::stream).collect(Collectors.toList());
        List<BaseTreeNodeDTO> tree = DatasetUtils.mergeDuplicateTree(treeNodes, "root");
        return tree;
    }

    /**
     * 将一行层级值拆成树节点，使用累积路径生成稳定主键并去重
     */
    private List<BaseTreeNodeDTO> buildTreeNode(String[] row, Set<String> pkSet) {
        List<BaseTreeNodeDTO> nodes = new ArrayList<>();
        List<String> parentPkList = new ArrayList<>();
        for (int i = 0; i < row.length; i++) {
            String text = row[i];
            if (StringUtils.isEmpty(text)) {
                continue;
            }
            parentPkList.add(text);
            String val = String.join(TreeUtils.SEPARATOR, parentPkList);
            String parentVal = i == 0 ? TreeUtils.DEFAULT_ROOT : row[i - 1];
            String pk = String.join(TreeUtils.SEPARATOR, parentPkList);
            if (pkSet.contains(pk)) continue;
            pkSet.add(pk);
            BaseTreeNodeDTO node = new BaseTreeNodeDTO(val, parentVal, StringUtils.isNotBlank(text) ? text.trim() : text, pk + TreeUtils.SEPARATOR + i);
            nodes.add(node);
        }
        return nodes;

    }

    /**
     * 用真实 schema 标识替换 SQL 中的内部 schema 别名
     */
    private static String replaceSchemaAlias(String sql, String schemaAlias, String replacement) {
        return SqlPlaceholderUtils.replaceIdentifier(sql, schemaAlias, replacement);
    }

    /**
     * 移除 SQL 中的内部 schema 前缀，适用于无显式 schema 的单源查询
     */
    private static String removeSchemaAliasPrefix(String sql, String schemaAlias) {
        return SqlPlaceholderUtils.removeIdentifierPrefix(sql, schemaAlias);
    }
}
