package io.crest.engine.trans;

import io.crest.constant.FieldTypeConstants;
import io.crest.engine.constant.ExtFieldConstant;
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
import io.crest.extensions.view.dto.ChartViewFieldDTO;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 将图表指标字段转换为 SQL 元数据中的纵轴字段、过滤和排序配置
 */
public class Quota2SQLObj {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$");

    /**
     * 解析指标字段并写入 SQLMeta 的纵轴字段、过滤和排序信息
     */
    public static void quota2sqlObj(SQLMeta meta, List<ChartViewFieldDTO> fields, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        SQLObj tableObj = meta.getTable();
        if (ObjectUtils.isEmpty(tableObj)) {
            return;
        }
        Map<String, String> paramMap = Utils.mergeParam(fieldParam, chartParam);
        List<SQLObj> yFields = new ArrayList<>();
        List<String> yWheres = new ArrayList<>();
        List<SQLObj> yOrders = new ArrayList<>();
        Map<String, String> fieldsDialect = new HashMap<>();

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        if (!CollectionUtils.isEmpty(fields)) {
            for (int i = 0; i < fields.size(); i++) {
                ChartViewFieldDTO y = fields.get(i);
                String originField;
                if (ObjectUtils.isNotEmpty(y.getExtField()) && Objects.equals(y.getExtField(), ExtFieldConstant.EXT_CALC)) {
                    // 解析origin name中有关联的字段生成sql表达式
                    String calcFieldExp = Utils.calcFieldRegex(y, tableObj, originFields, isCross, dsMap, paramMap, pluginManage);
                    // 给计算字段处加一个占位符，后续SQL方言转换后再替换
                    originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, y.getId());
                    fieldsDialect.put(originField, calcFieldExp);
                    if (isCross) {
                        originField = calcFieldExp;
                    }
                } else if (ObjectUtils.isNotEmpty(y.getExtField()) && Objects.equals(y.getExtField(), ExtFieldConstant.EXT_COPY)) {
                    originField = FieldSqlExpressionUtils.physicalField(tableObj, y, dsType);
                } else if (ObjectUtils.isNotEmpty(y.getExtField()) && Objects.equals(y.getExtField(), ExtFieldConstant.EXT_GROUP)) {
                    String groupFieldExp = Utils.transGroupFieldToSql(y, originFields, isCross, dsMap, pluginManage);
                    // 给计算字段处加一个占位符，后续SQL方言转换后再替换
                    originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, y.getId());
                    fieldsDialect.put(originField, groupFieldExp);
                    if (isCross) {
                        originField = groupFieldExp;
                    }
                } else {
                    originField = FieldSqlExpressionUtils.physicalField(tableObj, y, dsType);
                }
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_Y_PREFIX, i);
                // 处理纵轴字段
                SQLObj ySQLObj = getYFields(y, originField, fieldAlias);
                if (Strings.CI.equals("bar-range", meta.getChartType()) && Strings.CI.equals(y.getGroupType(), "d") && y.getFieldType() == 1) {
                    yFields.add(Dimension2SQLObj.getXFields(y, ySQLObj.getFieldName(), fieldAlias, isCross));
                } else {
                    yFields.add(ySQLObj);
                }
                // 处理纵轴过滤
                String wheres = getYWheres(y, originField, fieldAlias);
                if (ObjectUtils.isNotEmpty(wheres)) {
                    yWheres.add(wheres);
                }
                // 处理纵轴排序
                if (StringUtils.isNotEmpty(y.getSort()) && Utils.joinSort(y.getSort())) {
                    yOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(y.getSort())
                            .id(y.getId())
                            .build());
                }
            }
        }
        meta.setYFields(yFields);
        meta.setYWheres(yWheres);
        meta.setYOrders(yOrders);
        meta.setYFieldsDialect(fieldsDialect);
    }

    /**
     * 根据指标字段类型和汇总方式生成纵轴 SQL 字段
     */
    private static SQLObj getYFields(ChartViewFieldDTO y, String originField, String fieldAlias) {
        String fieldName = "";
        if (Strings.CI.equals(y.getOriginName(), "*")) {
            fieldName = SQLConstants.AGG_COUNT;
        } else if (SQLConstants.DIMENSION_TYPE.contains(y.getFieldType())) {
            if (Strings.CI.equals(y.getSummary(), "count_distinct")) {
                fieldName = String.format(SQLConstants.AGG_FIELD, "COUNT", "DISTINCT " + originField);
            } else {
                fieldName = String.format(SQLConstants.AGG_FIELD, y.getSummary(), originField);
            }
        } else {
            String normalizedOriginField = FieldSqlExpressionUtils.nullSafeTextOrigin(y, originField);
            if (Strings.CI.equals(y.getSummary(), "avg") || Strings.CI.contains(y.getSummary(), "pop")) {
                String cast = String.format(SQLConstants.CAST, normalizedOriginField, Objects.equals(y.getFieldType(), FieldTypeConstants.INTEGER) ? SQLConstants.DEFAULT_INT_FORMAT : SQLConstants.DEFAULT_FLOAT_FORMAT);
                String agg = String.format(SQLConstants.AGG_FIELD, y.getSummary(), cast);
                String cast1 = String.format(SQLConstants.CAST, agg, SQLConstants.DEFAULT_FLOAT_FORMAT);
                fieldName = String.format(SQLConstants.ROUND, cast1, "8");
            } else {
                String cast = String.format(SQLConstants.CAST, normalizedOriginField, Objects.equals(y.getFieldType(), FieldTypeConstants.INTEGER) ? SQLConstants.DEFAULT_INT_FORMAT : SQLConstants.DEFAULT_FLOAT_FORMAT);
                if (Strings.CI.equals(y.getSummary(), "count_distinct")) {
                    fieldName = String.format(SQLConstants.AGG_FIELD, "COUNT", "DISTINCT " + cast);
                } else if (y.getSummary() == null) {
                    // 透视表自定义汇总不用聚合
                    fieldName = cast;
                } else {
                    fieldName = String.format(SQLConstants.AGG_FIELD, y.getSummary(), cast);
                }
            }
        }

        return SQLObj.builder()
                .fieldName(fieldName)
                .fieldAlias(fieldAlias)
                .build();
    }

    /**
     * 将指标字段上的过滤配置转换为 SQL 条件
     */
    private static String getYWheres(ChartViewFieldDTO y, String originField, String fieldAlias) {
        List<SQLObj> list = new ArrayList<>();
        if (!CollectionUtils.isEmpty(y.getFilter()) && y.getFilter().size() > 0) {
            y.getFilter().forEach(f -> {
                String whereTerm = Utils.transFilterTerm(f.getTerm());
                String whereValue = "";
                // 原始类型不是时间，在de中被转成时间的字段做处理
                if (Strings.CI.equals(f.getTerm(), "null")) {
                    whereValue = "";
                } else if (Strings.CI.equals(f.getTerm(), "not_null")) {
                    whereValue = "";
                } else if (Strings.CI.equals(f.getTerm(), "empty")) {
                    whereValue = "''";
                } else if (Strings.CI.equals(f.getTerm(), "not_empty")) {
                    whereValue = "''";
                } else if (Strings.CI.contains(f.getTerm(), "in")) {
                    List<String> values = Arrays.stream(StringUtils.defaultString(f.getValue()).split(","))
                            .map(StringUtils::trim)
                            .toList();
                    if (isNumberLikeField(y)) {
                        whereValue = "(" + String.join(",", values.stream().map(Quota2SQLObj::sanitizeNumberLiteral).toList()) + ")";
                    } else {
                        whereValue = "(" + String.join(",", values.stream().map(Quota2SQLObj::toQuotedValue).toList()) + ")";
                    }
                } else if (Strings.CI.contains(f.getTerm(), "like")) {
                    whereValue = toLikeValue(f.getValue());
                } else {
                    if (isNumberLikeField(y)) {
                        whereValue = String.format(SQLConstants.WHERE_NUMBER_VALUE, sanitizeNumberLiteral(f.getValue()));
                    } else {
                        whereValue = String.format(SQLConstants.WHERE_VALUE_VALUE, sanitizeSqlLiteral(f.getValue()));
                    }
                }
                list.add(SQLObj.builder()
                        .whereField(fieldAlias)
                        .whereAlias(fieldAlias)
                        .whereTermAndValue(whereTerm + whereValue)
                        .build());
            });
        }
        List<String> strList = new ArrayList<>();
        list.forEach(ele -> strList.add(ele.getWhereField() + " " + ele.getWhereTermAndValue()));
        return !CollectionUtils.isEmpty(list) ? "(" + String.join(" " + Utils.getLogic(y.getLogic()) + " ", strList) + ")" : null;
    }

    /**
     * 判断字段是否应按数值字面量处理
     */
    private static boolean isNumberLikeField(ChartViewFieldDTO field) {
        return Objects.equals(field.getFieldType(), FieldTypeConstants.INTEGER)
                || Objects.equals(field.getFieldType(), FieldTypeConstants.FLOAT)
                || Objects.equals(field.getFieldType(), FieldTypeConstants.BOOLEAN);
    }

    /**
     * 校验并转义普通字符串 SQL 字面量
     */
    private static String sanitizeSqlLiteral(String value) {
        String normalized = StringUtils.defaultString(value);
        Utils.validateSqlInjectionRisk(normalized);
        return Utils.transValue(normalized);
    }

    /**
     * 将普通字符串转换为单引号包裹的 SQL 值
     */
    private static String toQuotedValue(String value) {
        return "'" + sanitizeSqlLiteral(value) + "'";
    }

    /**
     * 将普通字符串转换为 LIKE 查询值
     */
    private static String toLikeValue(String value) {
        return "'%" + sanitizeSqlLiteral(value) + "%'";
    }

    /**
     * 校验并返回安全的数值 SQL 字面量
     */
    private static String sanitizeNumberLiteral(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        if (!NUMBER_PATTERN.matcher(normalized).matches()) {
            CrestException.throwException("Illegal number filter value");
        }
        return normalized;
    }

}
