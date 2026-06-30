package io.crest.engine.trans;

import io.crest.api.chart.dto.ChartSortFieldDTO;
import io.crest.constant.FieldTypeConstants;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.constant.SQLConstants;
import io.crest.engine.utils.Utils;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.dto.CalParam;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.model.SQLObj;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.*;

// 将图表排序字段转换为 SQL ORDER BY 片段
public class Order2SQLObj {

    // 追加图表排序字段，普通字段、计算字段和分组字段分别生成对应排序表达式
    public static void getOrders(SQLMeta meta, List<ChartSortFieldDTO> sortFields, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        SQLObj tableObj = meta.getTable();
        List<SQLObj> xOrders = meta.getXOrders() == null ? new ArrayList<>() : meta.getXOrders();
        if (ObjectUtils.isEmpty(tableObj)) {
            return;
        }
        if (ObjectUtils.isNotEmpty(sortFields)) {
            int step = originFields.size();
            for (int i = step; i < (step + sortFields.size()); i++) {
                ChartSortFieldDTO sortField = sortFields.get(i - step);
                SQLObj order = buildSortField(sortField, tableObj, i, originFields, isCross, dsMap, fieldParam, chartParam, pluginManage);
                xOrders.add(order);
            }
            meta.setXOrders(xOrders);
        }
    }

    // 单个排序字段需要复用字段转换规则，保证 ORDER BY 与 SELECT 表达式一致
    private static SQLObj buildSortField(ChartSortFieldDTO f, SQLObj tableObj, int i, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        Map<String, String> paramMap = Utils.mergeParam(fieldParam, chartParam);
        String originField;

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }
        Map<String, String> fieldsDialect = new HashMap<>();

        if (ObjectUtils.isNotEmpty(f.getExtField()) && Objects.equals(f.getExtField(), ExtFieldConstant.EXT_CALC)) {
            // 解析origin name中有关联的字段生成sql表达式
            String calcFieldExp = Utils.calcFieldRegex(f, tableObj, originFields, isCross, dsMap, paramMap, pluginManage);
            // 给计算字段处加一个占位符，后续SQL方言转换后再替换
            originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, f.getId());
            fieldsDialect.put(originField, calcFieldExp);
            if (isCross) {
                originField = calcFieldExp;
            }
        } else if (ObjectUtils.isNotEmpty(f.getExtField()) && Objects.equals(f.getExtField(), ExtFieldConstant.EXT_COPY)) {
            originField = FieldSqlExpressionUtils.physicalField(tableObj, f, dsType);
        } else if (ObjectUtils.isNotEmpty(f.getExtField()) && Objects.equals(f.getExtField(), ExtFieldConstant.EXT_GROUP)) {
            String groupFieldExp = Utils.transGroupFieldToSql(f, originFields, isCross, dsMap, pluginManage);
            // 给计算字段处加一个占位符，后续SQL方言转换后再替换
            originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, f.getId());
            fieldsDialect.put(originField, groupFieldExp);
            if (isCross) {
                originField = groupFieldExp;
            }
        } else {
            originField = FieldSqlExpressionUtils.physicalField(tableObj, f, dsType);
        }
        String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
        String fieldName = "";
        // 处理横轴字段
        if (Objects.equals(f.getExtractedFieldType(), FieldTypeConstants.TIME)) {
            if (Objects.equals(f.getFieldType(), FieldTypeConstants.INTEGER) || Objects.equals(f.getFieldType(), FieldTypeConstants.FLOAT)) {
                fieldName = String.format(SQLConstants.UNIX_TIMESTAMP, originField);
            } else {
                // 如果都是时间类型，把date和time类型进行字符串拼接
                if (isCross) {
                    if (Strings.CI.equals(f.getType(), "date")) {
                        originField = String.format(SQLConstants.CREST_STR_TO_DATE, String.format(SQLConstants.CONCAT, originField, "' 00:00:00'"), SQLConstants.DEFAULT_DATE_FORMAT);
                    } else if (Strings.CI.equals(f.getType(), "time")) {
                        originField = String.format(SQLConstants.CREST_STR_TO_DATE, String.format(SQLConstants.CONCAT, "'1970-01-01 '", originField), SQLConstants.DEFAULT_DATE_FORMAT);
                    }
                }
                fieldName = originField;
            }
        } else if (Objects.equals(f.getExtractedFieldType(), FieldTypeConstants.STRING)) {
            String normalizedOriginField = FieldSqlExpressionUtils.nullSafeTextOrigin(f, originField);
            if (Objects.equals(f.getFieldType(), FieldTypeConstants.INTEGER)) {
                fieldName = String.format(SQLConstants.CAST, normalizedOriginField, SQLConstants.DEFAULT_INT_FORMAT);
            } else if (Objects.equals(f.getFieldType(), FieldTypeConstants.FLOAT)) {
                fieldName = String.format(SQLConstants.CAST, normalizedOriginField, SQLConstants.DEFAULT_FLOAT_FORMAT);
            } else if (Objects.equals(f.getFieldType(), FieldTypeConstants.TIME)) {
                fieldName = StringUtils.isEmpty(f.getDateFormat()) ? String.format(SQLConstants.CREST_STR_TO_DATE, normalizedOriginField, SQLConstants.DEFAULT_DATE_FORMAT) :
                        String.format(SQLConstants.CREST_DATE_FORMAT, String.format(SQLConstants.CREST_STR_TO_DATE, normalizedOriginField, f.getDateFormat()), SQLConstants.DEFAULT_DATE_FORMAT);
            } else {
                fieldName = originField;
            }
        } else {
            if (Objects.equals(f.getFieldType(), FieldTypeConstants.TIME)) {
                String cast = String.format(SQLConstants.CAST, originField, SQLConstants.DEFAULT_INT_FORMAT);
                fieldName = String.format(SQLConstants.FROM_UNIXTIME, cast, SQLConstants.DEFAULT_DATE_FORMAT);
            } else if (Objects.equals(f.getFieldType(), FieldTypeConstants.INTEGER)) {
                fieldName = String.format(SQLConstants.CAST, FieldSqlExpressionUtils.nullSafeTextOrigin(f, originField), SQLConstants.DEFAULT_INT_FORMAT);
            } else if (Objects.equals(f.getFieldType(), FieldTypeConstants.FLOAT)) {
                fieldName = String.format(SQLConstants.CAST, FieldSqlExpressionUtils.nullSafeTextOrigin(f, originField), SQLConstants.DEFAULT_FLOAT_FORMAT);
            } else {
                fieldName = originField;
            }
        }
        SQLObj result = SQLObj.builder()
                .orderField(String.format(SQLConstants.FIELD_DOT, fieldName))
                .orderAlias(String.format(SQLConstants.FIELD_DOT, fieldName))
                .orderDirection(f.getOrderDirection().equalsIgnoreCase("asc") ? "asc" : "desc").build();
        return result;
    }

}
