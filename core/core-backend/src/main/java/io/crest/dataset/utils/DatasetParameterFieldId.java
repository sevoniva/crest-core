package io.crest.dataset.utils;

/**
 * 数据集参数字段 ID 工具。
 */
public final class DatasetParameterFieldId {

    public static final String MARKER = "|DATASET_PARAM|";

    private DatasetParameterFieldId() {
    }

    /**
     * 生成数据集参数字段 ID。
     */
    public static String build(Long datasetTableId, String variableName) {
        return datasetTableId + MARKER + variableName;
    }

    /**
     * 判断字段 ID 是否指向数据集参数。
     */
    public static boolean matches(String fieldId) {
        return fieldId != null && fieldId.contains(MARKER);
    }
}
