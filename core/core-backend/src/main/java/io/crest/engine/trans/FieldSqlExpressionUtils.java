package io.crest.engine.trans;

import io.crest.constant.FieldTypeConstants;
import io.crest.constant.SQLConstants;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.i18n.Translator;
import io.crest.result.ResultCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Objects;

final class FieldSqlExpressionUtils {

    private FieldSqlExpressionUtils() {
    }

    static String nullSafeTextOrigin(DatasetTableFieldDTO field, String originField) {
        if (!needsTextNullNormalization(field)) {
            return originField;
        }
        return "CASE WHEN " + originField + " IS NULL THEN NULL "
                + "WHEN LOWER(TRIM(" + originField + ")) IN ('', 'null', '\\\\n') THEN NULL "
                + "ELSE TRIM(" + originField + ") END";
    }

    static String physicalField(SQLObj tableObj, DatasetTableFieldDTO field, String dsType) {
        if (field == null) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_gauge_field_change"));
        }
        String identifier;
        if (Strings.CI.equals(dsType, "es")) {
            identifier = field.getOriginName();
        } else {
            identifier = StringUtils.firstNonBlank(
                    field.getEngineFieldName(),
                    field.getFieldShortName(),
                    field.getDbFieldName()
            );
        }
        if (StringUtils.isBlank(identifier) || Strings.CI.equals(identifier, "null")) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_gauge_field_change"));
        }
        return String.format(SQLConstants.FIELD_NAME, tableObj.getTableAlias(), identifier);
    }

    // 只有文本底座字段在转换为数值或时间前需要归一化空值
    private static boolean needsTextNullNormalization(DatasetTableFieldDTO field) {
        if (field == null) {
            return false;
        }
        boolean textBacked = Objects.equals(field.getExtractedFieldType(), FieldTypeConstants.STRING)
                || Objects.equals(field.getExtractedFieldType(), FieldTypeConstants.LOCATION);
        boolean converted = Objects.equals(field.getFieldType(), FieldTypeConstants.TIME)
                || Objects.equals(field.getFieldType(), FieldTypeConstants.INTEGER)
                || Objects.equals(field.getFieldType(), FieldTypeConstants.FLOAT)
                || Objects.equals(field.getFieldType(), FieldTypeConstants.BOOLEAN);
        return textBacked && converted;
    }
}
