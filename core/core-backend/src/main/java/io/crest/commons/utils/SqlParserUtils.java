package io.crest.commons.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.api.permissions.variable.dto.SysVariableValueDto;
import io.crest.api.permissions.variable.dto.SysVariableValueItem;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.TableFieldWithValue;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.extensions.view.dto.SqlVariableDetails;
import io.crest.i18n.Translator;
import io.crest.utils.JsonUtil;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.math.BigDecimal;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.*;


import static io.crest.chart.manage.ChartDataManage.START_END_SEPARATOR;

// 处理 SQL 变量默认值、系统变量和预编译参数
public class SqlParserUtils {
    private static final String CREST_PARAM_PREFIX = "$CREST_PARAM{";
    public static final String SQL_PARAM_PREFIX = "$[";
    public static final String SYS_VARIABLE_PREFIX = "$crest[";
    private static final String SubstitutedSystemParamPrefix = "CrestSystemParams_";
    private UserFormVO userEntity;
    private static final String SubstitutedSql = " 'CREST-BI' = 'CREST-BI' ";
    private final List<Map<String, String>> sysParams = new ArrayList<>();
    TypeReference<List<SqlVariableDetails>> listTypeReference = new TypeReference<List<SqlVariableDetails>>() {
    };
    private List<SqlVariableDetails> defaultsSqlVariableDetails = new ArrayList<>();

    // 用变量默认值和用户上下文替换 SQL 占位符
    public String handleVariableDefaultValue(String sql, String sqlVariableDetails, boolean isEdit, boolean isFromDataSet, List<SqlVariableDetails> parameters, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, PluginManageApi pluginManage, UserFormVO userEntity) {
        return handleVariableDefaultValueWithPreparedParams(sql, sqlVariableDetails, isEdit, isFromDataSet, parameters, isCross, dsMap, pluginManage, userEntity).getSql();
    }

    // 替换 SQL 占位符并返回可绑定的预编译参数
    public SqlVariableHandleResult handleVariableDefaultValueWithPreparedParams(String sql, String sqlVariableDetails, boolean isEdit, boolean isFromDataSet, List<SqlVariableDetails> parameters, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, PluginManageApi pluginManage, UserFormVO userEntity) {
        DatasourceSchemaDTO ds = dsMap.entrySet().iterator().next().getValue();
        if (StringUtils.isEmpty(sql)) {
            CrestException.throwException(Translator.get("i18n_sql_not_empty"));
        }
        this.userEntity = userEntity;
        sql = sql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        if (StringUtils.isNotEmpty(sqlVariableDetails)) {
            defaultsSqlVariableDetails = JsonUtil.parseList(sqlVariableDetails, listTypeReference);
        }
        List<TableFieldWithValue> tableFieldWithValues = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder();
        int lastIndex = 0;
        for (DelimitedToken crestParam : findDelimitedTokens(sql, CREST_PARAM_PREFIX, '}')) {
            sqlBuilder.append(sql, lastIndex, crestParam.start());
            String sqlItem = crestParam.content();
            boolean replaceParam = false;
            List<TableFieldWithValue> sqlItemFieldWithValues = new ArrayList<>();
            StringBuilder sqlItemBuilder = new StringBuilder();
            int sqlItemLastIndex = 0;
            for (DelimitedToken sqlVariable : findDelimitedTokens(sqlItem, SQL_PARAM_PREFIX, ']')) {
                sqlItemBuilder.append(sqlItem, sqlItemLastIndex, sqlVariable.start());
                boolean replaceParamItem = false;
                String variableName = sqlVariable.content();
                SqlVariableDetails defaultsSqlVariableDetail = findSqlVariableDetail(defaultsSqlVariableDetails, variableName);
                SqlVariableDetails filterParameter = findSqlVariableDetail(parameters, variableName);
                if (filterParameter != null) {
                    PreparedSqlFragment preparedSqlFragment = buildPreparedSqlFragment(filterParameter);
                    boolean quoted = isQuotedVariable(sqlItem, sqlVariable.start(), sqlVariable.end());
                    if (quoted) {
                        sqlItemBuilder.setLength(sqlItemBuilder.length() - 1);
                    }
                    sqlItemBuilder.append(preparedSqlFragment.replacement());
                    sqlItemLastIndex = quoted ? sqlVariable.end() + 1 : sqlVariable.end();
                    sqlItemFieldWithValues.addAll(preparedSqlFragment.tableFieldWithValues());
                    replaceParamItem = true;
                } else {
                    if (defaultsSqlVariableDetail != null && StringUtils.isNotEmpty(defaultsSqlVariableDetail.getDefaultValue())) {
                        if (!isEdit && isFromDataSet && defaultsSqlVariableDetail.getDefaultValueScope().equals(SqlVariableDetails.DefaultValueScope.ALLSCOPE)) {
                            PreparedSqlFragment preparedSqlFragment = buildPreparedSqlFragmentForDefaultValue(defaultsSqlVariableDetail);
                            boolean quoted = isQuotedVariable(sqlItem, sqlVariable.start(), sqlVariable.end());
                            if (quoted) {
                                sqlItemBuilder.setLength(sqlItemBuilder.length() - 1);
                            }
                            sqlItemBuilder.append(preparedSqlFragment.replacement());
                            sqlItemLastIndex = quoted ? sqlVariable.end() + 1 : sqlVariable.end();
                            sqlItemFieldWithValues.addAll(preparedSqlFragment.tableFieldWithValues());
                            replaceParamItem = true;
                        }
                        if (isEdit) {
                            PreparedSqlFragment preparedSqlFragment = buildPreparedSqlFragmentForDefaultValue(defaultsSqlVariableDetail);
                            boolean quoted = isQuotedVariable(sqlItem, sqlVariable.start(), sqlVariable.end());
                            if (quoted) {
                                sqlItemBuilder.setLength(sqlItemBuilder.length() - 1);
                            }
                            sqlItemBuilder.append(preparedSqlFragment.replacement());
                            sqlItemLastIndex = quoted ? sqlVariable.end() + 1 : sqlVariable.end();
                            sqlItemFieldWithValues.addAll(preparedSqlFragment.tableFieldWithValues());
                            replaceParamItem = true;
                        }
                    }
                }
                if (!replaceParamItem) {
                    sqlItemBuilder.append(sqlItem, sqlItemLastIndex, sqlVariable.end());
                    sqlItemLastIndex = sqlVariable.end();
                }
                if (!replaceParamItem) {
                    replaceParam = false;
                    break;
                } else {
                    replaceParam = true;
                }
            }
            if (replaceParam) {
                sqlItemBuilder.append(sqlItem.substring(sqlItemLastIndex));
                sqlItem = sqlItemBuilder.toString();
            }
            for (DelimitedToken sysVariable : findDelimitedTokens(sqlItem, SYS_VARIABLE_PREFIX, ']')) {
                boolean replaceParamItem = false;

                String sysVariableToken = sqlItem.substring(sysVariable.start(), sysVariable.end());
                String sysVariableId = sysVariable.content();
                if (!isParams(sysVariableId)) {
                    continue;
                }
                sqlItem = sqlItem.replace(sysVariableToken, SubstitutedSystemParamPrefix + sysVariableId);
                try {
                    Expression expression = CCJSqlParserUtil.parseCondExpression(sqlItem);
                    String value = null;
                    if (expression instanceof InExpression) {
                        value = handleSubstitutedSqlForIn(sysVariableId);
                    } else {
                        value = handleSubstitutedSql(sysVariableId);
                    }
                    if (StringUtils.isNotEmpty(value)) {
                        sqlItem = sqlItem.replace(SubstitutedSystemParamPrefix + sysVariableId, value);
                        replaceParamItem = true;
                    }
                } catch (Exception e) {
                    io.crest.utils.LogUtil.error(e.getMessage(), e);
                }
                if (!replaceParamItem) {
                    replaceParam = false;
                    break;
                } else {
                    replaceParam = true;
                }
            }
            if (!replaceParam) {
                sqlBuilder.append(SubstitutedSql);
            } else {
                sqlBuilder.append(sqlItem);
                tableFieldWithValues.addAll(sqlItemFieldWithValues);
            }
            lastIndex = crestParam.end();
        }
        sqlBuilder.append(sql.substring(lastIndex));
        sql = sqlBuilder.toString();

        try {
            if (!isCross) {
                Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
                DatasourceSchemaDTO value = next.getValue();

                String prefix = "";
                String suffix = "";
                if (Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).toList().contains(value.getType())) {
                    DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(value.getType());
                    prefix = datasourceType.getPrefix();
                    suffix = datasourceType.getSuffix();
                } else {
                    List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
                    List<PluginDatasourceVO> list = pluginDatasourceList.stream().filter(ele -> Strings.CS.equals(ele.getType(), value.getType())).toList();
                    if (ObjectUtils.isNotEmpty(list)) {
                        PluginDatasourceVO first = list.get(0);
                        prefix = first.getPrefix();
                        suffix = first.getSuffix();
                    } else {
                        CrestException.throwException("当前数据源插件不存在");
                    }
                }

                for (DelimitedToken quotedIdentifier : findDelimitedTokens(sql, "`", '`')) {
                    sql = sql.replace("`" + quotedIdentifier.content() + "`", prefix + quotedIdentifier.content() + suffix);
                }
            }
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        SqlVariableHandleResult result = new SqlVariableHandleResult(sql);
        result.setTableFieldWithValues(tableFieldWithValues);
        return result;
    }

    // 查找指定分隔符包裹的变量片段
    private static List<DelimitedToken> findDelimitedTokens(String text, String prefix, char suffix) {
        List<DelimitedToken> tokens = new ArrayList<>();
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(prefix)) {
            return tokens;
        }
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int start = text.indexOf(prefix, searchFrom);
            if (start < 0) {
                break;
            }
            int contentStart = start + prefix.length();
            int endSuffix = text.indexOf(suffix, contentStart);
            if (endSuffix < 0) {
                break;
            }
            int end = endSuffix + 1;
            tokens.add(new DelimitedToken(start, end, text.substring(contentStart, endSuffix)));
            searchFrom = end;
        }
        return tokens;
    }

    // 判断系统变量标识是否允许参与替换
    private static boolean isParams(String paramId) {
        if (Arrays.asList("sysParams.userId", "sysParams.userEmail", "sysParams.userName", "sysParams.userPhone").contains(paramId)) {
            return true;
        }
        boolean isLong = false;
        try {
            Long.valueOf(paramId);
            isLong = true;
        } catch (Exception e) {
            isLong = false;
        }
        if (paramId.length() >= 18 && isLong) {
            return true;
        }
        return false;
    }


    // 按变量名查找 SQL 变量配置
    private SqlVariableDetails findSqlVariableDetail(List<SqlVariableDetails> sqlVariableDetails, String variableName) {
        if (CollectionUtils.isEmpty(sqlVariableDetails)) {
            return null;
        }
        for (SqlVariableDetails sqlVariableDetail : sqlVariableDetails) {
            if (Strings.CI.equals(variableName, sqlVariableDetail.getVariableName())) {
                return sqlVariableDetail;
            }
        }
        return null;
    }

    // 判断变量占位符是否已被单引号包裹
    private boolean isQuotedVariable(String sqlItem, int start, int end) {
        return start > 0
                && end < sqlItem.length()
                && sqlItem.charAt(start - 1) == '\''
                && sqlItem.charAt(end) == '\'';
    }

    // 构建用户传入变量的预编译 SQL 片段
    private PreparedSqlFragment buildPreparedSqlFragment(SqlVariableDetails sqlVariableDetails) {
        List<TableFieldWithValue> values = new ArrayList<>();
        List<String> replacements = new ArrayList<>();
        List<String> preparedValues = resolvePreparedValues(sqlVariableDetails);
        for (String preparedValue : preparedValues) {
            values.add(buildPreparedValue(sqlVariableDetails, preparedValue));
            replacements.add("?");
        }
        return new PreparedSqlFragment(String.join(",", replacements), values);
    }

    // 构建默认变量值的预编译 SQL 片段
    private PreparedSqlFragment buildPreparedSqlFragmentForDefaultValue(SqlVariableDetails sqlVariableDetails) {
        SqlVariableDetails defaultValueDetail = new SqlVariableDetails();
        defaultValueDetail.setVariableName(sqlVariableDetails.getVariableName());
        defaultValueDetail.setType(sqlVariableDetails.getType());
        defaultValueDetail.setFieldType(sqlVariableDetails.getFieldType());
        defaultValueDetail.setId(sqlVariableDetails.getId());
        defaultValueDetail.setOperator(sqlVariableDetails.getOperator());
        defaultValueDetail.setValue(Collections.singletonList(sqlVariableDetails.getDefaultValue()));
        return buildPreparedSqlFragment(defaultValueDetail);
    }

    // 解析变量值并按字段类型转换日期范围值
    private List<String> resolvePreparedValues(SqlVariableDetails sqlVariableDetails) {
        if (Strings.CS.equals(sqlVariableDetails.getOperator(), "in")) {
            if (sqlVariableDetails.getFieldType() == 1) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(sqlVariableDetails.getType().size() > 1 ? (String) sqlVariableDetails.getType().get(1).replace("DD", "dd").replace("YYYY", "yyyy") : "yyyy");
                if (Strings.CS.endsWith(sqlVariableDetails.getId(), START_END_SEPARATOR)) {
                    return Collections.singletonList(simpleDateFormat.format(new Date(Long.parseLong((String) sqlVariableDetails.getValue().get(1)))));
                }
                return Collections.singletonList(simpleDateFormat.format(new Date(Long.parseLong((String) sqlVariableDetails.getValue().get(0)))));
            }
            return CollectionUtils.isEmpty(sqlVariableDetails.getValue()) ? Collections.emptyList() : sqlVariableDetails.getValue();
        }
        if (Strings.CS.equals(sqlVariableDetails.getOperator(), "between") || Strings.CS.equals(sqlVariableDetails.getOperator(), "eq")) {
            if (sqlVariableDetails.getFieldType() == 1) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(sqlVariableDetails.getType().size() > 1 ? (String) sqlVariableDetails.getType().get(1).replace("DD", "dd").replace("YYYY", "yyyy") : "yyyy");
                if (Strings.CS.endsWith(sqlVariableDetails.getId(), START_END_SEPARATOR)) {
                    return Collections.singletonList(simpleDateFormat.format(new Date(Long.parseLong((String) sqlVariableDetails.getValue().get(1)))));
                }
                return Collections.singletonList(simpleDateFormat.format(new Date(Long.parseLong((String) sqlVariableDetails.getValue().get(0)))));
            }
            if (Strings.CS.endsWith(sqlVariableDetails.getId(), START_END_SEPARATOR)) {
                return Collections.singletonList(sqlVariableDetails.getValue().get(1));
            }
            return Collections.singletonList(sqlVariableDetails.getValue().get(0));
        }
        return CollectionUtils.isEmpty(sqlVariableDetails.getValue()) ? Collections.emptyList() : Collections.singletonList(sqlVariableDetails.getValue().get(0));
    }

    // 将变量值转换为引擎可绑定字段值
    private TableFieldWithValue buildPreparedValue(SqlVariableDetails sqlVariableDetails, String value) {
        TableFieldWithValue tableFieldWithValue = new TableFieldWithValue();
        tableFieldWithValue.setFiledName(sqlVariableDetails.getVariableName());
        tableFieldWithValue.setTerm(sqlVariableDetails.getOperator());
        tableFieldWithValue.setExtractedFieldType(sqlVariableDetails.getFieldType());
        if (sqlVariableDetails.getFieldType() == 2) {
            tableFieldWithValue.setType(Types.BIGINT);
            tableFieldWithValue.setColumnTypeName("BIGINT");
            tableFieldWithValue.setValue(Long.parseLong(value));
            return tableFieldWithValue;
        }
        if (sqlVariableDetails.getFieldType() == 3) {
            tableFieldWithValue.setType(Types.DECIMAL);
            tableFieldWithValue.setColumnTypeName("DECIMAL");
            tableFieldWithValue.setValue(new BigDecimal(value));
            return tableFieldWithValue;
        }
        if (sqlVariableDetails.getFieldType() == 4) {
            if (Strings.CI.equalsAny(value, "true", "false")) {
                tableFieldWithValue.setType(Types.BOOLEAN);
                tableFieldWithValue.setColumnTypeName("BOOLEAN");
                tableFieldWithValue.setValue(Boolean.parseBoolean(value));
            } else {
                tableFieldWithValue.setType(Types.INTEGER);
                tableFieldWithValue.setColumnTypeName("INTEGER");
                tableFieldWithValue.setValue(Integer.parseInt(value));
            }
            return tableFieldWithValue;
        }
        tableFieldWithValue.setType(Types.VARCHAR);
        tableFieldWithValue.setColumnTypeName("VARCHAR");
        tableFieldWithValue.setValue(value);
        return tableFieldWithValue;
    }

    // 记录分隔符变量片段的位置和内容
    private record DelimitedToken(int start, int end, String content) {
    }

    // 记录预编译替换片段及其绑定值
    private record PreparedSqlFragment(String replacement, List<TableFieldWithValue> tableFieldWithValues) {
    }

    // 将单值系统变量转换为 SQL 字面量
    private String handleSubstitutedSql(String sysVariableId) {
        if (userEntity != null) {
            if (sysVariableId.equalsIgnoreCase("sysParams.userId")) {
                return userEntity.getAccount();
            }
            if (sysVariableId.equalsIgnoreCase("sysParams.userEmail")) {
                return userEntity.getEmail();
            }
            if (sysVariableId.equalsIgnoreCase("sysParams.userName")) {
                return userEntity.getName();
            }
            if (sysVariableId.equalsIgnoreCase("sysParams.userPhone")) {
                return userEntity.getPhone();
            }
            for (SysVariableValueItem variable : userEntity.getVariables()) {
                if (!variable.isValid()) {
                    continue;
                }
                if (!sysVariableId.equalsIgnoreCase(variable.getVariableId().toString())) {
                    continue;
                }
                if (variable.getSysVariableDto().getType().equalsIgnoreCase("text")) {
                    for (SysVariableValueDto sysVariableValueDto : variable.getValueList()) {
                        if (variable.getVariableValueIds().contains(sysVariableValueDto.getId().toString())) {
                            return sysVariableValueDto.getValue();
                        }
                    }
                } else {
                    return variable.getVariableValue();
                }
            }
            return null;
        } else {
            return null;
        }
    }


    // 将多值系统变量转换为 IN 条件字面量
    private String handleSubstitutedSqlForIn(String sysVariableId) {
        if (userEntity != null) {
            for (SysVariableValueItem variable : userEntity.getVariables()) {
                List<String> values = new ArrayList<>();
                if (!variable.isValid()) {
                    continue;
                }
                if (!sysVariableId.equalsIgnoreCase(variable.getVariableId().toString())) {
                    continue;
                }
                if (variable.getSysVariableDto().getType().equalsIgnoreCase("text")) {
                    for (SysVariableValueDto sysVariableValueDto : variable.getValueList()) {
                        if (variable.getVariableValueIds().contains(sysVariableValueDto.getId().toString())) {
                            values.add(sysVariableValueDto.getValue());
                        }
                    }
                }
                if (CollectionUtils.isNotEmpty(values)) {
                    return "'" + String.join("','", values) + "'";
                }
            }
            return null;
        } else {
            return null;
        }
    }
}
