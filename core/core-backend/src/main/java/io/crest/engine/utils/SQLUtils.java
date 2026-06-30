package io.crest.engine.utils;

import java.util.Optional;

// 提供当前模块复用的工具能力
public class SQLUtils {
    // 转换输入内容并返回安全结果
    public static String transKeyword(String value) {
        return Optional.ofNullable(value).orElse("").replaceAll("'", "''").replaceAll("\\\\","\\\\\\\\").replace("\n", "\\n");
    }

    // 根据输入参数构造业务结果
    public static String buildOriginPreviewSql(String sql, int limit, int offset) {
        return "SELECT * FROM (" + sql + ") tmp LIMIT " + limit + " OFFSET " + offset;
    }

    // 根据输入参数构造业务结果
    public static String buildOriginPreviewSqlWithOrderBy(String sql, int limit, int offset, String orderBy) {
        return "SELECT * FROM (" + sql + ") tmp ORDER BY " + orderBy + " LIMIT " + limit + " OFFSET " + offset;
    }
}
