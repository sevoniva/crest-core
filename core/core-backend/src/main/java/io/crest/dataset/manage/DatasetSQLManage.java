package io.crest.dataset.manage;

import io.crest.api.dataset.union.*;
import io.crest.api.permissions.auth.dto.BusiPerCheckDTO;
import io.crest.api.permissions.dataset.api.RowPermissionsApi;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.commons.utils.SqlVariableHandleResult;
import io.crest.commons.utils.SqlParserUtils;
import io.crest.constant.AuthEnum;
import io.crest.constant.SQLConstants;
import io.crest.dataset.constant.DatasetTableType;
import io.crest.dataset.dao.auto.entity.CoreDatasetGroup;
import io.crest.dataset.dao.auto.mapper.CoreDatasetGroupMapper;
import io.crest.dataset.utils.DatasetTableTypeConstants;
import io.crest.dataset.utils.SqlUtils;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.manage.DataSourceManage;
import io.crest.datasource.manage.EngineManage;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.DsTypeDTO;
import io.crest.extensions.datasource.dto.TableFieldWithValue;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.extensions.view.dto.ChartExtFilterDTO;
import io.crest.extensions.view.dto.ChartExtRequest;
import io.crest.extensions.view.dto.SqlVariableDetails;
import io.crest.i18n.Translator;
import io.crest.system.manage.CorePermissionManage;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据集 SQL 组装器，负责把数据集联合树、字段选择、SQL 参数和权限上下文转换为可执行查询
 * 该类只生成 SQL、字段映射和数据源 schema 信息，不直接执行查询或保存数据集配置
 */
@Component
public class DatasetSQLManage {

    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private EngineManage engineManage;

    @Resource
    private CorePermissionManage corePermissionManage;

    @Autowired(required = false)
    private PluginManageApi pluginManage;
    @Autowired(required = false)
    private RowPermissionsApi rowPermissionsApi;
    @Resource
    private DataSourceManage dataSourceManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private CoreDatasetGroupMapper coreDatasetGroupMapper;

    /**
     * 返回可选的行权限服务，未启用权限模块时允许为空
     */
    private RowPermissionsApi getRowPermissionsApi() {
        return rowPermissionsApi;
    }

    private static Logger logger = LoggerFactory.getLogger(DatasetSQLManage.class);

    /**
     * 从图表过滤条件中筛选当前数据表关联的 SQL 参数，并携带运行时过滤值
     */
    private List<SqlVariableDetails> filterParameters(ChartExtRequest chartExtRequest, Long datasetTableId) {
        List<SqlVariableDetails> parameters = new ArrayList<>();
        if (chartExtRequest != null && ObjectUtils.isNotEmpty(chartExtRequest.getOuterParamsFilters())) {
            for (ChartExtFilterDTO filterDTO : chartExtRequest.getOuterParamsFilters()) {
                if (CollectionUtils.isEmpty(filterDTO.getValue())) {
                    continue;
                }
                filterParametersAdaptor(parameters, filterDTO, datasetTableId);
            }
        }
        if (chartExtRequest != null && ObjectUtils.isNotEmpty(chartExtRequest.getWebParamsFilters())) {
            for (ChartExtFilterDTO filterDTO : chartExtRequest.getWebParamsFilters()) {
                if (CollectionUtils.isEmpty(filterDTO.getValue())) {
                    continue;
                }
                filterParametersAdaptor(parameters, filterDTO, datasetTableId);
            }
        }
        if (chartExtRequest != null && ObjectUtils.isNotEmpty(chartExtRequest.getFilter())) {
            for (ChartExtFilterDTO filterDTO : chartExtRequest.getFilter()) {
                if (CollectionUtils.isEmpty(filterDTO.getValue())) {
                    continue;
                }
                filterParametersAdaptor(parameters, filterDTO, datasetTableId);
            }
        }
        return parameters;
    }

    /**
     * 将单个过滤条件中的 SQL 参数转换为当前数据表可用的参数值
     */
    private void filterParametersAdaptor(List<SqlVariableDetails> parameters, ChartExtFilterDTO filterDTO, Long datasetTableId) {
        if (ObjectUtils.isNotEmpty(filterDTO.getParameters())) {
            for (SqlVariableDetails parameter : filterDTO.getParameters()) {
                if (parameter.getDatasetTableId().equals(datasetTableId)) {
                    parameter.setValue(filterDTO.getValue());
                    parameter.setOperator(filterDTO.getOperator());
                    parameters.add(parameter);
                }
            }
        }
    }

    /**
     * 生成编辑态数据集 SQL，调用方不指定固定数据源时按数据集联合树自行解析
     */
    public Map<String, Object> getUnionSQLForEdit(DatasetGroupInfoDTO dataTableInfoDTO, ChartExtRequest chartExtRequest) throws Exception {
        return getUnionSQLForEdit(dataTableInfoDTO, chartExtRequest, null, chartExtRequest == null);
    }

    /**
     * 生成联合数据集 SQL，并返回 SQL 文本、选中字段、关联关系、数据源映射和预编译参数
     */
    public Map<String, Object> getUnionSQLForEdit(DatasetGroupInfoDTO dataTableInfoDTO, ChartExtRequest chartExtRequest, CoreDatasource coreDatasource, boolean isFromDataSet) throws Exception {
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        // 全量字段用于继承历史引擎字段名，保证编辑后缓存字段名保持稳定
        List<DatasetTableFieldDTO> allFields = dataTableInfoDTO.getAllFields();
        List<UnionDTO> union = dataTableInfoDTO.getUnion();
        // checkedInfo 记录每个表别名下最终进入 SELECT 的字段表达式
        Map<String, String[]> checkedInfo = new LinkedHashMap<>();
        List<UnionParamDTO> unionList = new ArrayList<>();
        List<DatasetTableFieldDTO> checkedFields = new ArrayList<>();
        List<TableFieldWithValue> tableFieldWithValues = new ArrayList<>();
        String sql = "";
        if (ObjectUtils.isEmpty(union)) {
            return null;
        }
        if (dataTableInfoDTO.getIsCross() == null) {
            mergeDatasetCrossDefault(dataTableInfoDTO);
        }
        boolean isCross = Boolean.TRUE.equals(dataTableInfoDTO.getIsCross());
        DatasetTableDTO currentDs = union.get(0).getCurrentDs();
        SQLObj tableName = null;
        for (int i = 0; i < union.size(); i++) {
            UnionDTO unionDTO = union.get(i);
            DatasetTableDTO datasetTable = unionDTO.getCurrentDs();
            DatasetTableInfoDTO tableInfo = JsonUtil.parseObject(datasetTable.getInfo(), DatasetTableInfoDTO.class);

            String schema;
            if (dsMap.containsKey(datasetTable.getDatasourceId())) {
                schema = dsMap.get(datasetTable.getDatasourceId()).getSchemaAlias();
            } else {
                schema = putObj2Map(dsMap, datasetTable, isCross, coreDatasource);
            }
            SQLObj table = getUnionTable(datasetTable, tableInfo, schema, i, filterParameters(chartExtRequest, currentDs.getId()), isFromDataSet, isCross, dsMap, tableFieldWithValues);
            if (i == 0) {
                tableName = table;
            }
            // 只把前端勾选字段写入 SELECT，并为每个字段生成稳定别名
            List<DatasetTableFieldDTO> fields = unionDTO.getCurrentDsFields();
            fields = fields.stream().filter(DatasetTableFieldDTO::getChecked).collect(Collectors.toList());

            String[] array = fields.stream()
                    .map(f -> {
                        inheritEngineFieldName(allFields, f);

                        String alias;
                        if (StringUtils.isNotBlank(f.getEngineFieldName())) {
                            alias = f.getEngineFieldName();
                        } else if (StringUtils.isNotBlank(f.getDbFieldName())) {
                            alias = f.getDbFieldName();
                        } else {
                            alias = TableUtils.fieldNameShort(f.getDatasetTableId() + "_" + f.getOriginName());
                        }

                        f.setFieldShortName(alias);
                        f.setEngineFieldName(f.getFieldShortName());
                        f.setDatasetTableId(datasetTable.getId());
                        String prefix = "";
                        String suffix = "";

                        DsTypeDTO datasourceType = datasourceType(dsMap, datasetTable.getDatasourceId());
                        if (Objects.equals(f.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                            if (isCross) {
                                prefix = "`";
                                suffix = "`";
                            } else {
                                prefix = datasourceType.getPrefix();
                                suffix = datasourceType.getSuffix();
                            }
                        }
                        if (Strings.CI.equals(datasourceType.getType(), "es")) {
                            return table.getTableAlias() + "." + prefix + f.getOriginName() + suffix;
                        } else {
                            return table.getTableAlias() + "." + prefix + sourceFieldName(f) + suffix + " AS " + prefix + alias + suffix;
                        }
                    })
                    .toArray(String[]::new);
            checkedInfo.put(table.getTableAlias(), array);
            checkedFields.addAll(fields);
            // 子节点递归时会继续补充字段表达式和 join 关系
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getUnionForEdit(datasetTable, table, unionDTO.getChildrenDs(), checkedInfo, unionList, checkedFields, dsMap, chartExtRequest, isCross, allFields, tableFieldWithValues);
            }
        }
        // 根据是否存在关联关系，决定生成单表查询或 join 查询
        boolean isFullJoin = false;
        if (!CollectionUtils.isEmpty(unionList)) {
            // 合并所有表别名下的 SELECT 字段表达式
            StringBuilder field = new StringBuilder();
            for (Map.Entry<String, String[]> next : checkedInfo.entrySet()) {
                if (next.getValue().length > 0) {
                    field.append(StringUtils.join(next.getValue(), ",")).append(",");
                }
            }
            String f = subPrefixSuffixChar(field.toString());
            // 按联合树顺序拼接 JOIN 片段和关联条件
            StringBuilder join = new StringBuilder();
            for (UnionParamDTO unionParamDTO : unionList) {
                // 前端关联类型转换为 SQL JOIN 关键字
                String joinType = convertUnionTypeToSQL(unionParamDTO.getUnionType());
                // 全连接标记返回给调用方，用于后续兼容不支持 FULL JOIN 的数据源
                if (!isFullJoin) {
                    if (Strings.CI.equals(unionParamDTO.getUnionType(), "full")) {
                        isFullJoin = true;
                    }
                }

                SQLObj parentSQLObj = unionParamDTO.getParentSQLObj();
                SQLObj currentSQLObj = unionParamDTO.getCurrentSQLObj();
                DatasetTableDTO parentDs = unionParamDTO.getParentDs();
                DatasetTableDTO currentDs1 = unionParamDTO.getCurrentDs();

                String ts = "";
                String tablePrefix = "";
                String tableSuffix = "";
                if (ObjectUtils.isNotEmpty(currentSQLObj.getTableSchema())) {
                    if (isCross) {
                        tablePrefix = "`";
                        tableSuffix = "`";
                    } else {
                        DsTypeDTO datasourceType = datasourceType(dsMap, currentDs1.getDatasourceId());
                        tablePrefix = datasourceType.getPrefix();
                        tableSuffix = datasourceType.getSuffix();
                    }

                    ts = tablePrefix + currentSQLObj.getTableSchema() + tableSuffix + ".";
                }
                // 当前表与父表之间的 ON 条件由前端配置的字段对组成
                join.append(" ").append(joinType).append(" ")
                        .append(ts)
                        .append(tablePrefix + currentSQLObj.getTableName() + tableSuffix)
                        .append(" ").append(currentSQLObj.getTableAlias()).append(" ")
                        .append(" ON ");
                if (unionParamDTO.getUnionFields().size() == 0) {
                    CrestException.throwException(Translator.get("i18n_union_field_can_not_empty"));
                }
                for (int i = 0; i < unionParamDTO.getUnionFields().size(); i++) {
                    UnionItemDTO unionItemDTO = unionParamDTO.getUnionFields().get(i);
                    // 关联字段沿用各自数据源的标识符引号规则，跨源时统一使用 Calcite 引号
                    DatasetTableFieldDTO parentField = unionItemDTO.getParentField();
                    DatasetTableFieldDTO currentField = unionItemDTO.getCurrentField();
                    String pPrefix = "";
                    String pSuffix = "";
                    if (Objects.equals(parentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        if (isCross) {
                            pPrefix = "`";
                            pSuffix = "`";
                        } else {
                            DsTypeDTO datasourceType = datasourceType(dsMap, parentDs.getDatasourceId());
                            pPrefix = datasourceType.getPrefix();
                            pSuffix = datasourceType.getSuffix();
                        }
                    }
                    String cPrefix = "";
                    String cSuffix = "";
                    if (Objects.equals(currentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        if (isCross) {
                            cPrefix = "`";
                            cSuffix = "`";
                        } else {
                            DsTypeDTO datasourceType = datasourceType(dsMap, currentDs1.getDatasourceId());
                            cPrefix = datasourceType.getPrefix();
                            cSuffix = datasourceType.getSuffix();
                        }
                    }
                    join.append(parentSQLObj.getTableAlias()).append(".")
                            .append(pPrefix + sourceFieldName(parentField) + pSuffix)
                            .append(" = ")
                            .append(currentSQLObj.getTableAlias()).append(".")
                            .append(cPrefix + sourceFieldName(currentField) + cSuffix);
                    if (i < unionParamDTO.getUnionFields().size() - 1) {
                        join.append(" AND ");
                    }
                }
            }
            if (StringUtils.isEmpty(f)) {
                CrestException.throwException(Translator.get("i18n_union_ds_no_checked"));
            }
            sql = MessageFormat.format("SELECT {0} FROM {1}", f, TableUtils.getTableAndAlias(tableName, datasourceType(dsMap, currentDs.getDatasourceId()), isCross)) + join.toString();
        } else {
            String f = StringUtils.join(checkedInfo.get(tableName.getTableAlias()), ",");
            if (StringUtils.isEmpty(f)) {
                CrestException.throwException(Translator.get("i18n_union_ds_no_checked"));
            }
            sql = MessageFormat.format("SELECT {0} FROM {1}", f, TableUtils.getTableAndAlias(tableName, datasourceType(dsMap, currentDs.getDatasourceId()), isCross));
        }
        logger.debug("calcite origin sql: " + sql);
        Map<String, Object> map = new HashMap<>();
        map.put("sql", sql);
        map.put("field", checkedFields);
        map.put("join", unionList);
        map.put("dsMap", dsMap);
        map.put("isFullJoin", isFullJoin);
        map.put("tableFieldWithValues", tableFieldWithValues);
        return map;
    }

    /**
     * 递归处理联合树子节点，持续累积 SELECT 字段、JOIN 关系和数据源映射
     */
    private void getUnionForEdit(DatasetTableDTO parentTable, SQLObj parentSQLObj,
                                 List<UnionDTO> childrenDs, Map<String, String[]> checkedInfo,
                                 List<UnionParamDTO> unionList, List<DatasetTableFieldDTO> checkedFields,
                                 Map<Long, DatasourceSchemaDTO> dsMap, ChartExtRequest chartExtRequest,
                                 boolean isCross, List<DatasetTableFieldDTO> allFields,
                                 List<TableFieldWithValue> tableFieldWithValues) throws Exception {
        for (int i = 0; i < childrenDs.size(); i++) {
            int index = unionList.size() + 1;

            UnionDTO unionDTO = childrenDs.get(i);
            DatasetTableDTO datasetTable = unionDTO.getCurrentDs();
            DatasetTableInfoDTO tableInfo = JsonUtil.parseObject(datasetTable.getInfo(), DatasetTableInfoDTO.class);

            String schema;
            if (dsMap.containsKey(datasetTable.getDatasourceId())) {
                schema = dsMap.get(datasetTable.getDatasourceId()).getSchemaAlias();
            } else {
                schema = putObj2Map(dsMap, datasetTable, isCross);
            }
            SQLObj table = getUnionTable(datasetTable, tableInfo, schema, index, filterParameters(chartExtRequest, datasetTable.getId()), chartExtRequest == null, isCross, dsMap, tableFieldWithValues);

            List<DatasetTableFieldDTO> fields = unionDTO.getCurrentDsFields();
            fields = fields.stream().filter(DatasetTableFieldDTO::getChecked).collect(Collectors.toList());

            String[] array = fields.stream()
                    .map(f -> {
                        inheritEngineFieldName(allFields, f);

                        String alias;
                        if (StringUtils.isNotBlank(f.getEngineFieldName())) {
                            alias = f.getEngineFieldName();
                        } else if (StringUtils.isNotBlank(f.getDbFieldName())) {
                            alias = f.getDbFieldName();
                        } else {
                            alias = TableUtils.fieldNameShort(f.getDatasetTableId() + "_" + f.getOriginName());
                        }

                        f.setFieldShortName(alias);
                        f.setEngineFieldName(f.getFieldShortName());
                        f.setDatasetTableId(datasetTable.getId());
                        String prefix = "";
                        String suffix = "";
                        if (Objects.equals(f.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                            if (isCross) {
                                prefix = "`";
                                suffix = "`";
                            } else {
                                DsTypeDTO datasourceType = datasourceType(dsMap, datasetTable.getDatasourceId());
                                prefix = datasourceType.getPrefix();
                                suffix = datasourceType.getSuffix();
                            }
                        }
                        return table.getTableAlias() + "." + prefix + sourceFieldName(f) + suffix + " AS " + prefix + alias + suffix;
                    })
                    .toArray(String[]::new);
            checkedInfo.put(table.getTableAlias(), array);
            checkedFields.addAll(fields);

            UnionParamDTO unionToParent = unionDTO.getUnionToParent();
            // 关联关系需要带上父子表和 SQL 别名，供顶层 JOIN 统一拼接
            unionToParent.setParentDs(parentTable);
            unionToParent.setParentSQLObj(parentSQLObj);
            unionToParent.setCurrentDs(datasetTable);
            unionToParent.setCurrentSQLObj(table);
            unionList.add(unionToParent);
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getUnionForEdit(datasetTable, table, unionDTO.getChildrenDs(), checkedInfo, unionList, checkedFields, dsMap, chartExtRequest, isCross, allFields, tableFieldWithValues);
            }
        }
    }

    /**
     * 收集联合树中引用的全部数据源 ID，用于判断数据集是否跨源
     */
    private Set<Long> getAllDs(List<UnionDTO> union) {
        Set<Long> set = new HashSet<>();
        if (CollectionUtils.isEmpty(union)) {
            return set;
        }
        for (UnionDTO unionDTO : union) {
            if (unionDTO == null || unionDTO.getCurrentDs() == null) {
                continue;
            }
            Long datasourceId = unionDTO.getCurrentDs().getDatasourceId();
            if (datasourceId != null) {
                set.add(datasourceId);
            }
            getChildrenDs(unionDTO.getChildrenDs(), set);
        }
        return set;
    }

    /**
     * 递归收集子节点数据源 ID
     */
    private void getChildrenDs(List<UnionDTO> childrenDs, Set<Long> set) {
        if (CollectionUtils.isEmpty(childrenDs)) {
            return;
        }
        for (UnionDTO unionDTO : childrenDs) {
            if (unionDTO == null || unionDTO.getCurrentDs() == null) {
                continue;
            }
            Long datasourceId = unionDTO.getCurrentDs().getDatasourceId();
            if (datasourceId != null) {
                set.add(datasourceId);
            }
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getChildrenDs(unionDTO.getChildrenDs(), set);
            }
        }
    }

    /**
     * 返回数据源标识符引号规则，内置数据源和插件数据源都在这里统一转换
     */
    private DsTypeDTO datasourceType(Map<Long, DatasourceSchemaDTO> dsMap, Long datasourceId) {
        DatasourceSchemaDTO datasourceSchemaDTO = dsMap.get(datasourceId);
        String type;
        if (datasourceSchemaDTO == null) {
            CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(datasourceId);
            if (coreDatasource == null) {
                CrestException.throwException(Translator.get("i18n_dataset_ds_error") + ",ID:" + datasourceId);
            }
            type = engineManage.getEngineDatasource().getType();
        } else {
            type = datasourceSchemaDTO.getType();
        }

        if (Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).toList().contains(type)) {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(type);
            DsTypeDTO dto = new DsTypeDTO();
            BeanUtils.copyBean(dto, datasourceType);
            return dto;
        } else {
            List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
            List<PluginDatasourceVO> list = pluginDatasourceList.stream().filter(ele -> Strings.CS.equals(ele.getType(), type)).toList();
            if (ObjectUtils.isNotEmpty(list)) {
                PluginDatasourceVO first = list.get(0);
                DsTypeDTO dto = new DsTypeDTO();
                dto.setName(first.getName());
                dto.setCatalog(first.getCategory());
                dto.setType(first.getType());
                dto.setPrefix(first.getPrefix());
                dto.setSuffix(first.getSuffix());
                return dto;
            } else {
                CrestException.throwException(Translator.get("i18n_dataset_plugin_error"));
            }
            return null;
        }
    }

    /**
     * 移除字段表达式首尾多余逗号，避免生成非法 SELECT 列表
     */
    public String subPrefixSuffixChar(String str) {
        while (Strings.CS.startsWith(str, ",")) {
            str = str.substring(1, str.length());
        }
        while (Strings.CS.endsWith(str, ",")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * 将前端关联类型转换为 SQL JOIN 关键字
     */
    private String convertUnionTypeToSQL(String unionType) {
        switch (unionType) {
            case "1:1":
            case "inner":
                return " INNER JOIN ";
            case "1:N":
            case "left":
                return " LEFT JOIN ";
            case "N:1":
            case "right":
                return " RIGHT JOIN ";
            case "N:N":
            case "full":
                return " FULL JOIN ";
            default:
                return " INNER JOIN ";
        }
    }

    /**
     * 返回当前用户信息，SQL 参数默认值解析会用它计算用户相关变量
     */
    private UserFormVO getUserEntity() {
        if (getRowPermissionsApi() == null) {
            return null;
        }
        return getRowPermissionsApi().getUserById(AuthUtils.getUser().getUserId());
    }

    /**
     * 构造联合树中的单表 SQL 对象。自定义 SQL 会先解析变量，再作为子查询参与联合
     */
    private SQLObj getUnionTable(DatasetTableDTO currentDs, DatasetTableInfoDTO infoDTO, String tableSchema, int index, List<SqlVariableDetails> parameters, boolean isFromDataSet, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<TableFieldWithValue> tableFieldWithValues) {
        SQLObj tableObj;
        String tableAlias = String.format(SQLConstants.TABLE_ALIAS_PREFIX, index);
        if (Strings.CI.equals(currentDs.getType(), DatasetTableTypeConstants.DATASET_TABLE_DB)) {
            tableObj = SQLObj.builder().tableSchema(tableSchema).tableName(infoDTO.getTable()).tableAlias(tableAlias).build();
        } else if (Strings.CI.equals(currentDs.getType(), DatasetTableTypeConstants.DATASET_TABLE_SQL)) {
            Provider provider = ProviderFactory.getProvider(dsMap.entrySet().iterator().next().getValue().getType());
            // 自定义 SQL 参数在这里转换为默认值或预编译参数，避免后续拼接阶段丢失参数上下文
            String s = new String(Base64.getDecoder().decode(infoDTO.getSql()));
            SqlVariableHandleResult sqlResult = new SqlParserUtils().handleVariableDefaultValueWithPreparedParams(s, currentDs.getSqlVariableDetails(), false, isFromDataSet, parameters, isCross, dsMap, pluginManage, getUserEntity());
            String sql = sqlResult.getSql();
            tableFieldWithValues.addAll(sqlResult.getTableFieldWithValues());
            sql = provider.replaceComment(sql);
            // 跨源查询需要把自定义 SQL 中的表引用补上 Calcite schema
            if (isCross) {
                sql = SqlUtils.addSchema(sql, tableSchema);
            }
            tableObj = SQLObj.builder().tableSchema("").tableName("(" + sql + ")").tableAlias(tableAlias).build();
        } else {
            // Excel 和 API 同步表已经落在引擎库中，按普通物理表参与联合
            tableObj = SQLObj.builder().tableSchema(tableSchema).tableName(infoDTO.getTable()).tableAlias(tableAlias).build();
        }
        return tableObj;
    }

    /**
     * 返回源表字段名，优先使用数据库字段名，缺省时回退到原始字段名
     */
    private String sourceFieldName(DatasetTableFieldDTO field) {
        return StringUtils.defaultIfBlank(field.getDbFieldName(), field.getOriginName());
    }

    /**
     * 从全量字段列表继承已保存的引擎字段名和短字段名，保持编辑前后的字段别名稳定
     */
    static void inheritEngineFieldName(List<DatasetTableFieldDTO> allFields, DatasetTableFieldDTO field) {
        if (CollectionUtils.isEmpty(allFields) || field == null) {
            return;
        }
        for (DatasetTableFieldDTO candidate : allFields) {
            if (!sameDatasetField(candidate, field)) {
                continue;
            }
            if (StringUtils.isNotBlank(candidate.getEngineFieldName())) {
                field.setEngineFieldName(candidate.getEngineFieldName());
            }
            if (StringUtils.isNotBlank(candidate.getFieldShortName())) {
                field.setFieldShortName(candidate.getFieldShortName());
            }
            if (StringUtils.isBlank(field.getDbFieldName()) && StringUtils.isNotBlank(candidate.getDbFieldName())) {
                field.setDbFieldName(candidate.getDbFieldName());
            }
            return;
        }
    }

    /**
     * 判断两个字段是否指向同一数据集字段，优先比较 ID，其次比较表 ID、数据库字段名和原始名
     */
    private static boolean sameDatasetField(DatasetTableFieldDTO candidate, DatasetTableFieldDTO field) {
        if (candidate == null || field == null) {
            return false;
        }
        if (candidate.getId() != null && field.getId() != null) {
            return Objects.equals(candidate.getId(), field.getId());
        }
        if (candidate.getDatasetTableId() != null && field.getDatasetTableId() != null
                && !Objects.equals(candidate.getDatasetTableId(), field.getDatasetTableId())) {
            return false;
        }
        if (StringUtils.isNotBlank(candidate.getDbFieldName()) && StringUtils.isNotBlank(field.getDbFieldName())) {
            return Objects.equals(candidate.getDbFieldName(), field.getDbFieldName())
                    && compatibleOriginName(candidate, field);
        }
        return candidate.getDatasetTableId() != null && field.getDatasetTableId() != null
                && compatibleOriginName(candidate, field);
    }

    /**
     * 判断两个字段原始名是否兼容，空原始名按历史配置兼容处理
     */
    private static boolean compatibleOriginName(DatasetTableFieldDTO candidate, DatasetTableFieldDTO field) {
        if (StringUtils.isBlank(candidate.getOriginName()) || StringUtils.isBlank(field.getOriginName())) {
            return true;
        }
        return Objects.equals(candidate.getOriginName(), field.getOriginName());
    }

    /**
     * 将数据表所属数据源放入 schema 映射，并返回该表在 SQL 中使用的 schema 名称
     */
    public String putObj2Map(Map<Long, DatasourceSchemaDTO> dsMap, DatasetTableDTO ds, boolean isCross) {
        return putObj2Map(dsMap, ds, isCross, null);
    }

    /**
     * 将数据源写入 schema 映射。未传入数据源实体时，会先校验当前用户的数据源读取权限
     */
    public String putObj2Map(Map<Long, DatasourceSchemaDTO> dsMap, DatasetTableDTO ds, boolean isCross, CoreDatasource coreDatasource) {
        // 通过数据源 ID 校验读取权限，避免构造 SQL 时绕过数据源权限边界
        if (ObjectUtils.isEmpty(coreDatasource)) {
            BusiPerCheckDTO dto = new BusiPerCheckDTO();
            dto.setId(ds.getDatasourceId());
            dto.setAuthEnum(AuthEnum.READ);
            boolean checked = corePermissionManage.checkAuth(dto);
            if (!checked) {
                CrestException.throwException(Translator.get("i18n_no_datasource_permission"));
            }
        }
        String schemaAlias;
        if (Strings.CI.equals(ds.getType(), DatasetTableType.DB) || Strings.CI.equals(ds.getType(), DatasetTableType.SQL)) {
            if (ObjectUtils.isEmpty(coreDatasource)) {
                coreDatasource = dataSourceManage.getCoreDatasource(ds.getDatasourceId());
                if (coreDatasource == null) {
                    CrestException.throwException(Translator.get("i18n_dataset_ds_error") + ",ID:" + ds.getDatasourceId());
                }
                if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name()) || coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
                    coreDatasource = engineManage.getEngineDatasource();
                }
            }

            Map map = JsonUtil.parseObject(coreDatasource.getConfiguration(), Map.class);
            if (!isCross && ObjectUtils.isNotEmpty(map.get("schema"))) {
                schemaAlias = (String) map.get("schema");
            } else {
                schemaAlias = String.format(SQLConstants.SCHEMA, coreDatasource.getId());
            }

            if (!dsMap.containsKey(coreDatasource.getId())) {
                DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
                BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
                datasourceSchemaDTO.setSchemaAlias(schemaAlias);
                dsMap.put(coreDatasource.getId(), datasourceSchemaDTO);
            }
        } else if (Strings.CI.equals(ds.getType(), DatasetTableType.Es)) {
            if (ObjectUtils.isEmpty(coreDatasource)) {
                coreDatasource = dataSourceManage.getCoreDatasource(ds.getDatasourceId());
            }
            schemaAlias = String.format(SQLConstants.SCHEMA, coreDatasource.getId());
            if (!dsMap.containsKey(coreDatasource.getId())) {
                DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
                BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
                datasourceSchemaDTO.setSchemaAlias(schemaAlias);
                dsMap.put(coreDatasource.getId(), datasourceSchemaDTO);
            }
        } else {
            if (ObjectUtils.isEmpty(coreDatasource)) {
                coreDatasource = engineManage.getEngineDatasource();
            }
            schemaAlias = String.format(SQLConstants.SCHEMA, coreDatasource.getId());
            if (!dsMap.containsKey(coreDatasource.getId())) {
                DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
                BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
                datasourceSchemaDTO.setSchemaAlias(schemaAlias);
                dsMap.put(coreDatasource.getId(), datasourceSchemaDTO);
            }
        }
        return schemaAlias;
    }

    /**
     * 批量刷新历史数据集的跨源标记，用于补齐旧数据的默认值
     */
    public void datasetCrossDefault() {
        List<DatasetGroupInfoDTO> allList = datasetGroupManage.getAllList();
        for (DatasetGroupInfoDTO ele : allList) {
            mergeDatasetCrossDefault(ele);
            CoreDatasetGroup record = new CoreDatasetGroup();
            BeanUtils.copyBean(record, ele);
            coreDatasetGroupMapper.updateById(record);
        }
    }

    /**
     * 根据联合树中的数据源数量计算单个数据集是否跨源
     */
    public void mergeDatasetCrossDefault(DatasetGroupInfoDTO ele) {
        Set<Long> allDs = getAllDs(ele.getUnion());
        boolean isCross = allDs.size() > 1;
        ele.setIsCross(isCross);
    }
}
