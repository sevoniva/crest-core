package io.crest.engine.trans;

import io.crest.constant.SQLConstants;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.dto.CalParam;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.view.filter.DynamicTimeSetting;
import io.crest.extensions.view.filter.FilterTreeItem;
import io.crest.extensions.view.filter.FilterTreeObj;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 将自定义筛选树转换为 SQL WHERE 片段
public class CustomWhere2Str {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$");

    // 将自定义筛选树写入 SQL 元数据
    public static void customWhere2sqlObj(SQLMeta meta, FilterTreeObj tree, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        SQLObj tableObj = meta.getTable();
        if (ObjectUtils.isEmpty(tableObj)) {
            return;
        }
        List<String> res = new ArrayList<>();
        // 解析每个筛选树，并将多个筛选树以 AND 连接
        // 子树节点递归合并为一组条件
        if (ObjectUtils.isEmpty(tree)) {
            return;
        }
        Map<String, String> fieldsDialect = new HashMap<>();
        String treeExp = transTreeToWhere(tableObj, tree, fieldsDialect, originFields, isCross, dsMap, fieldParam, chartParam, pluginManage);
        if (StringUtils.isNotEmpty(treeExp)) {
            res.add(treeExp);
        }
        meta.setCustomWheres(ObjectUtils.isNotEmpty(res) ? "(" + String.join(" AND ", res) + ")" : null);
        meta.setCustomWheresDialect(fieldsDialect);
    }

    // 递归转换筛选树为 WHERE 表达式
    private static String transTreeToWhere(SQLObj tableObj, FilterTreeObj tree, Map<String, String> fieldsDialect, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        if (ObjectUtils.isEmpty(tree)) {
            return null;
        }
        String logic = tree.getLogic();
        List<FilterTreeItem> items = tree.getItems();
        List<String> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(items)) {
            // 根据节点类型转换单个筛选项或递归子树
            for (FilterTreeItem item : items) {
                String exp = null;
                if (Strings.CI.equals(item.getType(), "item")) {
                    // 单个筛选项先拼接 SQL，最后按逻辑关系汇总
                    exp = transTreeItem(tableObj, item, fieldsDialect, originFields, isCross, dsMap, fieldParam, chartParam, pluginManage);
                } else if (Strings.CI.equals(item.getType(), "tree")) {
                    // 递归转换子树
                    exp = transTreeToWhere(tableObj, item.getSubTree(), fieldsDialect, originFields, isCross, dsMap, fieldParam, chartParam, pluginManage);
                }
                if (StringUtils.isNotEmpty(exp)) {
                    list.add(exp);
                }
            }
        }
        return CollectionUtils.isNotEmpty(list) ? "(" + String.join(" " + logic + " ", list) + ")" : null;
    }

    // 转换单个筛选项为 SQL 条件
    private static String transTreeItem(SQLObj tableObj, FilterTreeItem item, Map<String, String> fieldsDialect, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        String res = null;
        DatasetTableFieldDTO field = item.getField();

        if (ObjectUtils.isEmpty(field)) {
            return null;
        }

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        Map<String, String> paramMap = Utils.mergeParam(fieldParam, chartParam);
        String whereName = "";
        String originName;
        if (ObjectUtils.isNotEmpty(field.getExtField()) && field.getExtField() == 2) {
            // 解析原始字段中的关联字段并生成 SQL 表达式
            String calcFieldExp = Utils.calcFieldRegex(field, tableObj, originFields, isCross, dsMap, paramMap, pluginManage);
            // 为计算字段保留占位符，后续 SQL 方言转换后再替换
            originName = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, field.getId());
            fieldsDialect.put(originName, calcFieldExp);
            if (isCross) {
                originName = calcFieldExp;
            }
        } else if (ObjectUtils.isNotEmpty(field.getExtField()) && field.getExtField() == 1) {
            originName = FieldSqlExpressionUtils.physicalField(tableObj, field, dsType);
        } else if (ObjectUtils.isNotEmpty(field.getExtField()) && field.getExtField() == 3) {
            String groupFieldExp = Utils.transGroupFieldToSql(field, originFields, isCross, dsMap, pluginManage);
            // 为分组字段保留占位符，后续 SQL 方言转换后再替换
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
                // 此处获取标准格式的日期
                whereName = String.format(SQLConstants.CREST_STR_TO_DATE, FieldSqlExpressionUtils.nullSafeTextOrigin(field, originName), StringUtils.isNotEmpty(field.getDateFormat()) ? field.getDateFormat() : SQLConstants.DEFAULT_DATE_FORMAT);
            }
            if (field.getExtractedFieldType() == 2 || field.getExtractedFieldType() == 3 || field.getExtractedFieldType() == 4) {
                String cast = String.format(SQLConstants.CAST, originName, SQLConstants.DEFAULT_INT_FORMAT);
                // 此处获取标准格式的日期
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
                // 此处获取标准格式的日期
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
            if (ObjectUtils.isNotEmpty(item.getEnumValue())) {
                if ((Strings.CI.contains(field.getType(), "NVARCHAR")
                        || Strings.CI.contains(field.getType(), "NCHAR"))
                        && !isCross
                        && Strings.CI.equals(dsType, DatasourceConfiguration.DatasourceType.sqlServer.getType())) {
                    res = "(" + whereName + " IN (" + item.getEnumValue().stream().map(CustomWhere2Str::toSqlServerNQuotedValue).collect(Collectors.joining(",")) + "))";
                } else {
                    res = "(" + whereName + " IN (" + item.getEnumValue().stream().map(CustomWhere2Str::toQuotedValue).collect(Collectors.joining(",")) + "))";
                }
            }
        } else {
            if (field.getFieldType() == 1 && isCross) {
                // 规定几种日期格式，一一匹配，匹配到就是该格式
                whereName = String.format(SQLConstants.UNIX_TIMESTAMP, whereName);
            }

            String value = item.getValue();
            String whereTerm = Utils.transFilterTerm(item.getTerm());
            String whereValue = "";

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
                    whereValue = "(" + Arrays.stream(value.split(",")).map(CustomWhere2Str::toSqlServerNQuotedValue).collect(Collectors.joining(",")) + ")";
                } else {
                    whereValue = "(" + Arrays.stream(value.split(",")).map(CustomWhere2Str::toQuotedValue).collect(Collectors.joining(",")) + ")";
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
                    // 如果是动态时间，计算具体值
                    value = fixValue(item);
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
                        if (field.getFieldType() == 2
                                || field.getFieldType() == 3
                                || field.getFieldType() == 4) {
                            whereValue = String.format(SQLConstants.WHERE_NUMBER_VALUE, sanitizeNumberLiteral(value));
                        } else {
                            whereValue = String.format(SQLConstants.WHERE_VALUE_VALUE, sanitizeSqlLiteral(value));
                        }
                    }
                }
            }
            SQLObj build = SQLObj.builder()
                    .whereField(whereName)
                    .whereTermAndValue(whereTerm + whereValue)
                    .build();
            res = build.getWhereField() + " " + build.getWhereTermAndValue();
        }
        return res;
    }

    // 判断数据源类型是否为 Oracle 兼容方言
    private static boolean isOracleLike(String dsType) {
        return Strings.CI.equalsAny(dsType,
                DatasourceConfiguration.DatasourceType.oracle.getType(),
                DatasourceConfiguration.DatasourceType.obOracle.getType());
    }

    // 清理并校验 SQL 字符串字面量
    private static String sanitizeSqlLiteral(String value) {
        String normalized = StringUtils.defaultString(value);
        Utils.validateSqlInjectionRisk(normalized);
        return Utils.transValue(normalized);
    }

    // 转换为带引号的 SQL 字符串值
    private static String toQuotedValue(String value) {
        return "'" + sanitizeSqlLiteral(value) + "'";
    }

    // 转换为 LIKE 条件使用的 SQL 字符串值
    private static String toLikeValue(String value) {
        return "'%" + sanitizeSqlLiteral(value) + "%'";
    }

    // 转换为 SQL Server Unicode 字符串值
    private static String toSqlServerNQuotedValue(String value) {
        return "'" + SQLConstants.MSSQL_N_PREFIX + sanitizeSqlLiteral(value) + "'";
    }

    // 转换为 SQL Server Unicode LIKE 字符串值
    private static String toSqlServerNLikeValue(String value) {
        return "'" + SQLConstants.MSSQL_N_PREFIX + "%" + sanitizeSqlLiteral(value) + "%'";
    }

    // 校验并清理数值筛选值
    private static String sanitizeNumberLiteral(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        if (!NUMBER_PATTERN.matcher(normalized).matches()) {
            CrestException.throwException("Illegal number filter value");
        }
        return normalized;
    }

    // 修正动态时间筛选值
    private static String fixValue(FilterTreeItem item) {
        if (StringUtils.isNotEmpty(item.getFilterTypeTime()) && Strings.CI.equals(item.getFilterTypeTime(), "dynamicDate")) {
            DynamicTimeSetting dynamicTimeSetting = item.getDynamicTimeSetting();
            Calendar instance = Calendar.getInstance();
            if (Strings.CI.equals(dynamicTimeSetting.getTimeGranularity(), "year")) {
                if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "thisYear")) {
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "lastYear")) {
                    instance.add(Calendar.YEAR, -1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "custom")) {
                    if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                        instance.add(Calendar.YEAR, -dynamicTimeSetting.getTimeNum());
                    } else {
                        instance.add(Calendar.YEAR, dynamicTimeSetting.getTimeNum());
                    }
                }
                return "" + instance.get(Calendar.YEAR);
            } else if (Strings.CI.equals(dynamicTimeSetting.getTimeGranularity(), "month")) {
                if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "thisMonth")) {
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "lastMonth")) {
                    instance.add(Calendar.MONTH, -1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "custom")) {
                    if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "year")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.YEAR, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.YEAR, dynamicTimeSetting.getTimeNum());
                        }
                    } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "month")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.MONTH, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.MONTH, dynamicTimeSetting.getTimeNum());
                        }
                    }
                }
                return instance.get(Calendar.YEAR) + "-" + (instance.get(Calendar.MONTH) + 1 < 10 ? "0" : "") + (instance.get(Calendar.MONTH) + 1);
            } else if (Strings.CI.equals(dynamicTimeSetting.getTimeGranularity(), "date")) {
                if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "today")) {
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "yesterday")) {
                    instance.add(Calendar.DAY_OF_MONTH, -1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "monthBeginning")) {
                    instance.set(Calendar.DAY_OF_MONTH, 1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "yearBeginning")) {
                    instance.set(Calendar.MONTH, 0);
                    instance.set(Calendar.DAY_OF_MONTH, 1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "custom")) {
                    if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "year")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.YEAR, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.YEAR, dynamicTimeSetting.getTimeNum());
                        }
                    } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "month")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.MONTH, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.MONTH, dynamicTimeSetting.getTimeNum());
                        }
                    } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "date")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.DAY_OF_MONTH, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.DAY_OF_MONTH, dynamicTimeSetting.getTimeNum());
                        }
                    }
                }
                return instance.get(Calendar.YEAR) + "-" + (instance.get(Calendar.MONTH) + 1 < 10 ? "0" : "") + (instance.get(Calendar.MONTH) + 1) + "-" + (instance.get(Calendar.DAY_OF_MONTH) < 10 ? "0" : "") + instance.get(Calendar.DAY_OF_MONTH);
            } else if (Strings.CI.equals(dynamicTimeSetting.getTimeGranularity(), "datetime")) {
                String time = " 00:00:00";
                if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "today")) {
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "yesterday")) {
                    instance.add(Calendar.DAY_OF_MONTH, -1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "monthBeginning")) {
                    instance.set(Calendar.DAY_OF_MONTH, 1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "yearBeginning")) {
                    instance.set(Calendar.MONTH, 0);
                    instance.set(Calendar.DAY_OF_MONTH, 1);
                } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrent(), "custom")) {
                    time = " " + dynamicTimeSetting.getArbitraryTime().substring(11, 19);
                    if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "year")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.YEAR, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.YEAR, dynamicTimeSetting.getTimeNum());
                        }
                    } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "month")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.MONTH, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.MONTH, dynamicTimeSetting.getTimeNum());
                        }
                    } else if (Strings.CI.equals(dynamicTimeSetting.getRelativeToCurrentType(), "date")) {
                        if (Strings.CI.equals(dynamicTimeSetting.getAround(), "f")) {
                            instance.add(Calendar.DAY_OF_MONTH, -dynamicTimeSetting.getTimeNum());
                        } else {
                            instance.add(Calendar.DAY_OF_MONTH, dynamicTimeSetting.getTimeNum());
                        }
                    }
                }
                return instance.get(Calendar.YEAR) + "-" + (instance.get(Calendar.MONTH) + 1 < 10 ? "0" : "") + (instance.get(Calendar.MONTH) + 1) + "-" + (instance.get(Calendar.DAY_OF_MONTH) < 10 ? "0" : "") + instance.get(Calendar.DAY_OF_MONTH) + time;
            }
        }
        return item.getValue();
    }
}
