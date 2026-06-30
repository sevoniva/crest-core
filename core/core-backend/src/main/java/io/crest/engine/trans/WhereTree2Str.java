package io.crest.engine.trans;

import io.crest.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.crest.constant.SQLConstants;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.utils.Utils;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.dto.CalParam;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.view.dto.DatasetRowPermissionsTreeItem;
import io.crest.extensions.view.dto.DatasetRowPermissionsTreeObj;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 行权限条件树到 SQL where 片段的转换工具
 */
public class WhereTree2Str {
    /**
     * 将多棵行权限过滤树转换为 SQLMeta 的 where 条件
     */
    public static void transFilterTrees(SQLMeta meta, List<DataSetRowPermissionsTreeDTO> requestList, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        SQLObj tableObj = meta.getTable();
        if (ObjectUtils.isEmpty(tableObj)) {
            return;
        }
        if (CollectionUtils.isEmpty(requestList)) {
            return;
        }
        List<String> res = new ArrayList<>();
        List<String> exportFilters = new ArrayList<>();
        Map<String, String> fieldsDialect = new HashMap<>();
        // 逐棵解析权限树，子树节点通过递归合并为一组条件
        for (DataSetRowPermissionsTreeDTO request : requestList) {
            DatasetRowPermissionsTreeObj tree = request.getTree();
            if (ObjectUtils.isEmpty(tree)) {
                continue;
            }
            String treeExp = transTreeToWhere(tableObj, tree, originFields, fieldsDialect, isCross, dsMap, fieldParam, chartParam, pluginManage);
            if (StringUtils.isNotEmpty(treeExp) && !request.isExportData()) {
                res.add(treeExp);
            }
            if (StringUtils.isNotEmpty(treeExp) && request.isExportData()) {
                exportFilters.add(treeExp);
            }
        }
        String whereSql = null;
        if (CollectionUtils.isNotEmpty(res)) {
            whereSql = String.join(" OR ", res);
        }
        if (CollectionUtils.isNotEmpty(exportFilters)) {
            whereSql = whereSql == null ? String.join(" and ", exportFilters) : whereSql + " AND " + String.join(" and ", exportFilters);
        }
        meta.setWhereTrees(whereSql != null ? "(" + whereSql + ")" : null);
        meta.setWhereTreesDialect(fieldsDialect);
    }

    /**
     * 将单棵权限树递归转换为 SQL 条件
     */
    private static String transTreeToWhere(SQLObj tableObj, DatasetRowPermissionsTreeObj tree, List<DatasetTableFieldDTO> originFields, Map<String, String> fieldsDialect, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        if (ObjectUtils.isEmpty(tree)) {
            return null;
        }
        String logic = tree.getLogic();
        List<DatasetRowPermissionsTreeItem> items = tree.getItems();
        List<String> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(items)) {
            // 节点类型为条件项或子树
            for (DatasetRowPermissionsTreeItem item : items) {
                String exp = null;
                if (Strings.CI.equals(item.getType(), "item")) {
                    // 单个条件项先转换为 SQL，最后按逻辑关系汇总
                    exp = transTreeItem(tableObj, item, originFields, fieldsDialect, isCross, dsMap, fieldParam, chartParam, pluginManage);
                } else if (Strings.CI.equals(item.getType(), "tree")) {
                    // 子树继续递归转换
                    exp = transTreeToWhere(tableObj, item.getSubTree(), originFields, fieldsDialect, isCross, dsMap, fieldParam, chartParam, pluginManage);
                }
                if (StringUtils.isNotEmpty(exp)) {
                    list.add(exp);
                }
            }
        }
        return CollectionUtils.isNotEmpty(list) ? "(" + String.join(" " + logic + " ", list) + ")" : null;
    }

    /**
     * 将单个权限树条件项转换为 SQL 条件片段
     */
    public static String transTreeItem(SQLObj tableObj, DatasetRowPermissionsTreeItem item, List<DatasetTableFieldDTO> originFields, Map<String, String> fieldsDialect, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        String res = null;
        DatasetTableFieldDTO field = item.getField();
        if (ObjectUtils.isEmpty(field)) {
            return null;
        }
        Map<String, String> paramMap = Utils.mergeParam(fieldParam, chartParam);
        String whereName = "";
        String originName;

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        if (ObjectUtils.isNotEmpty(field.getExtField()) && Objects.equals(field.getExtField(), ExtFieldConstant.EXT_CALC)) {
            // 解析origin name中有关联的字段生成sql表达式
            String calcFieldExp = Utils.calcFieldRegex(field, tableObj, originFields, isCross, dsMap, paramMap, pluginManage);
            // 给计算字段处加一个占位符，后续SQL方言转换后再替换
            originName = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, field.getId());
            fieldsDialect.put(originName, calcFieldExp);
            if (isCross) {
                originName = calcFieldExp;
            }
        } else if (ObjectUtils.isNotEmpty(field.getExtField()) && Objects.equals(field.getExtField(), ExtFieldConstant.EXT_COPY)) {
            originName = FieldSqlExpressionUtils.physicalField(tableObj, field, dsType);
        } else if (ObjectUtils.isNotEmpty(field.getExtField()) && Objects.equals(field.getExtField(), ExtFieldConstant.EXT_GROUP)) {
            String groupFieldExp = Utils.transGroupFieldToSql(field, originFields, isCross, dsMap, pluginManage);
            // 给计算字段处加一个占位符，后续SQL方言转换后再替换
            originName = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, field.getId());
            fieldsDialect.put(originName, groupFieldExp);
            if (isCross) {
                originName = groupFieldExp;
            }
        } else {
            originName = FieldSqlExpressionUtils.physicalField(tableObj, field, dsType);
        }
        if (field.getFieldType() == 1) {
            if (field.getExtractedFieldType() == 0 || field.getExtractedFieldType() == 5) {
                whereName = String.format(SQLConstants.CREST_STR_TO_DATE, FieldSqlExpressionUtils.nullSafeTextOrigin(field, originName), StringUtils.isNotEmpty(field.getDateFormat()) ? field.getDateFormat() : SQLConstants.DEFAULT_DATE_FORMAT);
            }
            if (field.getExtractedFieldType() == 2 || field.getExtractedFieldType() == 3 || field.getExtractedFieldType() == 4) {
                String cast = String.format(SQLConstants.CAST, originName, SQLConstants.DEFAULT_INT_FORMAT);
                whereName = String.format(SQLConstants.FROM_UNIXTIME, cast, SQLConstants.DEFAULT_DATE_FORMAT);
            }
            if (field.getExtractedFieldType() == 1) {
                // 如果都是时间类型，把date和time类型进行字符串拼接
                if (isCross) {
                    if (Strings.CI.equals(field.getType(), "date")) {
                        originName = String.format(SQLConstants.CREST_STR_TO_DATE, String.format(SQLConstants.CONCAT, originName, "' 00:00:00'"), SQLConstants.DEFAULT_DATE_FORMAT);
                    } else if (Strings.CI.equals(field.getType(), "time")) {
                        originName = String.format(SQLConstants.CREST_STR_TO_DATE, String.format(SQLConstants.CONCAT, "'1970-01-01 '", originName), SQLConstants.DEFAULT_DATE_FORMAT);
                    }
                }
                if (Strings.CI.equals(field.getType(), "date")
                        || (isOracleLike(dsMap.entrySet().iterator().next().getValue().getType()) && Strings.CI.equals(field.getType(), "timestamp"))) {
                    whereName = String.format(SQLConstants.CREST_CAST_DATE_FORMAT, originName,
                            SQLConstants.DEFAULT_DATE_FORMAT,
                            SQLConstants.DEFAULT_DATE_FORMAT);
                } else {
                    whereName = originName;
                }
            }
        } else if (field.getFieldType() == 2 || field.getFieldType() == 3) {
            if (field.getExtractedFieldType() == 0 || field.getExtractedFieldType() == 5) {
                whereName = String.format(SQLConstants.CAST, FieldSqlExpressionUtils.nullSafeTextOrigin(field, originName), SQLConstants.DEFAULT_FLOAT_FORMAT);
            }
            if (field.getExtractedFieldType() == 1) {
                whereName = String.format(SQLConstants.UNIX_TIMESTAMP, originName);
            }
            if (field.getExtractedFieldType() == 2 || field.getExtractedFieldType() == 4) {
                whereName = String.format(SQLConstants.CAST, originName, SQLConstants.DEFAULT_INT_FORMAT);
            }
            if (field.getExtractedFieldType() == 3) {
                whereName = String.format(SQLConstants.CAST, originName, SQLConstants.DEFAULT_FLOAT_FORMAT);
            }
        } else {
            whereName = originName;
        }

        if (Strings.CI.equals(item.getFilterType(), "enum")) {
            if (CollectionUtils.isNotEmpty(item.getEnumValue())) {
                if ((Strings.CI.contains(field.getType(), "NVARCHAR")
                        || Strings.CI.contains(field.getType(), "NCHAR"))
                        && !isCross
                        && Strings.CI.equals(dsType, DatasourceConfiguration.DatasourceType.sqlServer.getType())) {
                    res = "(" + whereName + " IN (" + item.getEnumValue().stream().map(WhereTree2Str::toSqlServerNQuotedValue).collect(Collectors.joining(",")) + "))";
                } else {
                    res = "(" + whereName + " IN (" + item.getEnumValue().stream().map(WhereTree2Str::toQuotedValue).collect(Collectors.joining(",")) + "))";
                }
            }
        } else {
            String value = item.getValue();
            String whereTerm = Utils.transFilterTerm(item.getTerm());
            String whereValue = "";

            if (field.getFieldType() == 1 && isCross) {
                whereName = String.format(SQLConstants.UNIX_TIMESTAMP, whereName);
            }

            if (Strings.CI.equals(item.getTerm(), "null")) {
                whereValue = "";
            } else if (Strings.CI.equals(item.getTerm(), "not_null")) {
                whereValue = "";
            } else if (Strings.CI.equals(item.getTerm(), "empty")) {
                whereValue = "''";
            } else if (Strings.CI.equals(item.getTerm(), "not_empty")) {
                whereValue = "''";
            } else if (Strings.CI.contains(item.getTerm(), "in") || Strings.CI.contains(item.getTerm(), "not in")) {
                if ((Strings.CI.contains(field.getType(), "NVARCHAR")
                        || Strings.CI.contains(field.getType(), "NCHAR"))
                        && !isCross
                        && Strings.CI.equals(dsType, DatasourceConfiguration.DatasourceType.sqlServer.getType())) {
                    whereValue = "(" + Arrays.stream(value.split(",")).map(WhereTree2Str::toSqlServerNQuotedValue).collect(Collectors.joining(",")) + ")";
                } else {
                    whereValue = "(" + Arrays.stream(value.split(",")).map(WhereTree2Str::toQuotedValue).collect(Collectors.joining(",")) + ")";
                }
            } else if (Strings.CI.contains(item.getTerm(), "like")) {
                if ((Strings.CI.contains(field.getType(), "NVARCHAR")
                        || Strings.CI.contains(field.getType(), "NCHAR"))
                        && !isCross
                        && Strings.CI.equals(dsType, DatasourceConfiguration.DatasourceType.sqlServer.getType())) {
                    whereValue = toSqlServerNLikeValue(value);
                } else {
                    whereValue = toLikeValue(value);
                }
            } else {
                // 如果是时间字段过滤，当条件是等于和不等于的时候转换成between和not between
                if (field.getFieldType() == 1) {
                    Map<String, Long> stringLongMap = Utils.parseDateTimeValue(value);
                    if (Strings.CI.equals(whereTerm, " = ")) {
                        whereTerm = " BETWEEN ";
                        // 把value类似过滤组件处理，获得start time和end time
                        if (isCross) {
                            whereValue = String.format(SQLConstants.WHERE_VALUE_BETWEEN, stringLongMap.get("startTime"), stringLongMap.get("endTime"));
                        } else {
                            whereValue = String.format(SQLConstants.WHERE_BETWEEN, Utils.transLong2Str(stringLongMap.get("startTime")), Utils.transLong2Str(stringLongMap.get("endTime")));
                        }
                    } else if (Strings.CI.equals(whereTerm, " <> ")) {
                        whereTerm = " NOT BETWEEN ";
                        if (isCross) {
                            whereValue = String.format(SQLConstants.WHERE_VALUE_BETWEEN, stringLongMap.get("startTime"), stringLongMap.get("endTime"));
                        } else {
                            whereValue = String.format(SQLConstants.WHERE_BETWEEN, Utils.transLong2Str(stringLongMap.get("startTime")), Utils.transLong2Str(stringLongMap.get("endTime")));
                        }
                    } else {
                        Long startTime = stringLongMap.get("startTime");
                        Long endTime = stringLongMap.get("endTime");
                        if (isCross) {
                            if (Strings.CI.equals(whereTerm, " > ") || Strings.CI.equals(whereTerm, " <= ")) {
                                value = endTime + "";
                            } else if (Strings.CI.equals(whereTerm, " >= ") || Strings.CI.equals(whereTerm, " < ")) {
                                value = startTime + "";
                            }
                        } else {
                            if (Strings.CI.equals(whereTerm, " > ") || Strings.CI.equals(whereTerm, " <= ")) {
                                value = Utils.transLong2Str(endTime);
                            } else if (Strings.CI.equals(whereTerm, " >= ") || Strings.CI.equals(whereTerm, " < ")) {
                                value = Utils.transLong2Str(startTime);
                            }
                        }
                        whereValue = String.format(SQLConstants.WHERE_VALUE_VALUE, sanitizeSqlLiteral(value));
                    }
                } else {
                    if ((Strings.CI.contains(field.getType(), "NVARCHAR")
                            || Strings.CI.contains(field.getType(), "NCHAR"))
                            && !isCross
                            && Strings.CI.equals(dsType, DatasourceConfiguration.DatasourceType.sqlServer.getType())) {
                        whereValue = String.format(SQLConstants.WHERE_VALUE_VALUE_CH, sanitizeSqlLiteral(value));
                    } else {
                        whereValue = String.format(SQLConstants.WHERE_VALUE_VALUE, sanitizeSqlLiteral(value));
                    }
                }
            }
            SQLObj build = SQLObj.builder().whereField(whereName).whereTermAndValue(whereTerm + whereValue).build();
            res = build.getWhereField() + " " + build.getWhereTermAndValue();
        }
        return res;
    }

    /**
     * 校验并转义 SQL 字面量
     */
    private static String sanitizeSqlLiteral(String value) {
        String normalized = StringUtils.defaultString(value);
        Utils.validateSqlInjectionRisk(normalized);
        return Utils.transValue(normalized);
    }

    /**
     * 判断数据源类型是否属于 Oracle 语义
     */
    private static boolean isOracleLike(String dsType) {
        return Strings.CI.equalsAny(dsType,
                DatasourceConfiguration.DatasourceType.oracle.getType(),
                DatasourceConfiguration.DatasourceType.obOracle.getType());
    }

    /**
     * 将值包装为普通 SQL 字符串字面量
     */
    private static String toQuotedValue(String value) {
        return "'" + sanitizeSqlLiteral(value) + "'";
    }

    /**
     * 将值包装为普通 SQL like 字面量
     */
    private static String toLikeValue(String value) {
        return "'%" + sanitizeSqlLiteral(value) + "%'";
    }

    /**
     * 将值包装为 SQL Server Unicode 字符串字面量
     */
    private static String toSqlServerNQuotedValue(String value) {
        return "'" + SQLConstants.MSSQL_N_PREFIX + sanitizeSqlLiteral(value) + "'";
    }

    /**
     * 将值包装为 SQL Server Unicode like 字面量
     */
    private static String toSqlServerNLikeValue(String value) {
        return "'" + SQLConstants.MSSQL_N_PREFIX + "%" + sanitizeSqlLiteral(value) + "%'";
    }
}
