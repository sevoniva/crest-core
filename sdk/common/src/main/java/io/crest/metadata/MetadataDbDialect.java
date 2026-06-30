package io.crest.metadata;

import java.sql.Timestamp;
import java.util.List;

/**
 * Crest 支持的元数据库差异 SQL 片段。
 */
public interface MetadataDbDialect {

    MetadataDbType type();

    String quoteIdentifier(String identifier);

    String limitOffset(String sql, long limit, long offset);

    default String limitOne(String sql) {
        return limitOffset(sql, 1, 0);
    }

    String ifNull(String expression, String fallback);

    String concat(String... expressions);

    String currentTimestamp();

    default String timestampParameter() {
        return "?";
    }

    default Object timestampValue(String value) {
        return Timestamp.valueOf(value);
    }

    String caseInsensitiveLike(String expression, String parameterExpression);

    String stringLiteral(String value);

    String stringCast(String expression);

    String nullableString();

    String collate(String expression);

    default String stringEquals(String leftExpression, String rightExpression) {
        return collate(leftExpression) + " = " + collate(rightExpression);
    }

    default String stringNotEquals(String leftExpression, String rightExpression) {
        return collate(leftExpression) + " <> " + collate(rightExpression);
    }

    default String existsFlag(String subquery) {
        return "CASE WHEN EXISTS (" + subquery + ") THEN 1 ELSE 0 END";
    }

    String numberCast(String expression);

    String insertIgnore(String table, String columns, String values);

    String mergeIfMissing(String table, String columns, String values, String conflictPredicate);

    String upsert(String table, String columns, String values, String conflictPredicate, List<String> updateAssignments);
}
