package io.crest.engine.trans;

import io.crest.constant.FieldTypeConstants;
import io.crest.constant.SQLConstants;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.func.FunctionConstant;
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

import static io.crest.constant.SQLConstants.FIELD_DOT_FIX;

// 将数据集字段转换为 SELECT 字段描述，兼容计算字段、分组字段和 SQLBot 别名
public class Field2SQLObj {

    // 按图表字段顺序生成查询字段，并保留需要按数据库方言二次转换的表达式
    public static void field2sqlObj(SQLMeta meta, List<DatasetTableFieldDTO> fields, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage, boolean forSqlbot) {
        SQLObj tableObj = meta.getTable();
        if (ObjectUtils.isEmpty(tableObj)) {
            return;
        }
        Map<String, String> paramMap = Utils.mergeParam(fieldParam, chartParam);
        List<SQLObj> xFields = new ArrayList<>();
        Map<String, String> fieldsDialect = new HashMap<>();

        String dsType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            dsType = next.getValue().getType();
        }

        if (ObjectUtils.isNotEmpty(fields)) {
            Set<String> aliasSet = new HashSet<>();
            for (int i = 0; i < fields.size(); i++) {
                DatasetTableFieldDTO x = fields.get(i);
                String originField;
                if (ObjectUtils.isNotEmpty(x.getExtField()) && Objects.equals(x.getExtField(), ExtFieldConstant.EXT_CALC)) {
                    // 解析origin name中有关联的字段生成sql表达式
                    String calcFieldExp = Utils.calcFieldRegex(x, tableObj, originFields, isCross, dsMap, paramMap, pluginManage);
                    // 给计算字段处加一个占位符，后续SQL方言转换后再替换
                    originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, x.getId());
                    fieldsDialect.put(originField, calcFieldExp);
                    if (isCross) {
                        originField = calcFieldExp;
                    }
                    // 此处是数据集预览，获取数据库原始字段枚举值等操作使用，如果遇到聚合函数则将originField设置为null
                    for (String func : FunctionConstant.AGG_FUNC) {
                        if (Utils.matchFunction(func, calcFieldExp)) {
                            originField = null;
                            break;
                        }
                    }
                } else if (ObjectUtils.isNotEmpty(x.getExtField()) && Objects.equals(x.getExtField(), ExtFieldConstant.EXT_COPY)) {
                    originField = FieldSqlExpressionUtils.physicalField(tableObj, x, dsType);
                } else if (ObjectUtils.isNotEmpty(x.getExtField()) && Objects.equals(x.getExtField(), ExtFieldConstant.EXT_GROUP)) {
                    String groupFieldExp = Utils.transGroupFieldToSql(x, originFields, isCross, dsMap, pluginManage);
                    // 给计算字段处加一个占位符，后续SQL方言转换后再替换
                    originField = String.format(SqlPlaceholderConstants.CALC_FIELD_PLACEHOLDER, x.getId());
                    fieldsDialect.put(originField, groupFieldExp);
                    if (isCross) {
                        originField = groupFieldExp;
                    }
                } else {
                    originField = FieldSqlExpressionUtils.physicalField(tableObj, x, dsType);
                }
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                if (forSqlbot) {
                    fieldAlias = x.getOriginName();
                    if (ObjectUtils.isNotEmpty(x.getExtField()) && !x.getExtField().equals(ExtFieldConstant.EXT_NORMAL) && StringUtils.isNotBlank(x.getName())) {
                        fieldAlias = x.getName();
                    }
                    if (aliasSet.contains(fieldAlias)) {
                        fieldAlias += ('_' + String.valueOf(i));
                    }
                    aliasSet.add(fieldAlias);
                    fieldAlias = String.format(FIELD_DOT_FIX, fieldAlias);
                }
                // 处理横轴字段
                xFields.add(getXFields(x, originField, fieldAlias, isCross));
            }
        }
        meta.setXFields(xFields);
        meta.setXFieldsDialect(fieldsDialect);
    }

    // 普通图表查询不使用 SQLBot 专属字段别名
    public static void field2sqlObj(SQLMeta meta, List<DatasetTableFieldDTO> fields, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, List<CalParam> fieldParam, List<CalParam> chartParam, PluginManageApi pluginManage) {
        field2sqlObj(meta, fields, originFields, isCross, dsMap, fieldParam, chartParam, pluginManage, false);
    }

    // 处理字段结构并补齐查询所需信息
    public static SQLObj getXFields(DatasetTableFieldDTO f, String originField, String fieldAlias, boolean isCross) {
        String fieldName = "";
        if (originField != null) {
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
                            String.format(SQLConstants.CREST_STR_TO_DATE, normalizedOriginField, f.getDateFormat());
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
        } else {
            fieldName = "'-'";
        }
        return SQLObj.builder()
                .fieldName(fieldName)
                .fieldAlias(fieldAlias)
                .id(f.getId())
                .build();
    }

}
