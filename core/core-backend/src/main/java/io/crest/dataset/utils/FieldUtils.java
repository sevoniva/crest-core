package io.crest.dataset.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// 提供当前模块复用的工具能力
public class FieldUtils {
    // 处理字段结构并补齐查询所需信息
    public static int resolveFieldType(final String type) {
        if (type == null || type.isBlank()) {
            return 0;
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        List<String> text = Arrays.asList("CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "ENUM", "ANY", "STRING", "BOOL", "BOOLEAN");
        List<String> time = Arrays.asList("DATE", "TIME", "YEAR", "DATETIME", "TIMESTAMP", "DATEV2", "DATETIMEV2", "DATETIME2", "DATETIMEOFFSET", "SMALLDATETIME", "DATETIME64", "_TIMESTAMPTZ", "TIMESTAMPTZ");
        List<String> num = Arrays.asList("INT", "SMALLINT", "MEDIUMINT", "INTEGER", "BIGINT", "LONG", "INT2", "INT4", "INT8", "INT16", "INT32", "INT64", "UINT8", "UINT16", "UINT32", "UINT64");
        List<String> doubleList = Arrays.asList("NUMBER", "FLOAT", "DOUBLE", "DECIMAL", "REAL", "MONEY", "NUMERIC", "FLOAT4", "FLOAT8", "DECFLOAT", "FLOAT32", "FLOAT64");
        List<String> boolType = Arrays.asList("BIT", "TINYINT");
        if (boolType.contains(normalizedType)) {
            return 4;// 布尔
        }
        if (doubleList.contains(normalizedType)) {
            return 3;// 浮点
        }
        if (num.contains(normalizedType)) {
            return 2;// 整型
        }
        if (time.contains(normalizedType)) {
            return 1;// 时间
        }
        if (text.contains(normalizedType)) {
            return 0;// 文本
        }

        if (boolType.stream().anyMatch(normalizedType::contains)) {
            return 4;// 布尔
        }
        if (doubleList.stream().anyMatch(normalizedType::contains)) {
            return 3;// 浮点
        }
        if (num.stream().anyMatch(normalizedType::contains)) {
            return 2;// 整型
        }
        if (time.stream().anyMatch(normalizedType::contains)) {
            return 1;// 时间
        }
        return 0;// 文本
    }

    // 处理字段结构并补齐查询所需信息
    public static String resolveFieldGroup(int fieldType) {
        switch (fieldType) {
            case 0:
            case 1:
            case 5:
                return "d";
            case 2:
            case 3:
            case 4:
                return "q";
            default:
                return "d";
        }
    }
}
