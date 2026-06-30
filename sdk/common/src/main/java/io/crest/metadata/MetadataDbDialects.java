package io.crest.metadata;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据库方言工厂及内置实现。
 */
public final class MetadataDbDialects {

    private static final MetadataDbDialect OB_ORACLE = new ObOracleDialect();

    private MetadataDbDialects() {
    }

    // 按当前运行环境解析元数据库方言，供业务 SQL 片段生成复用
    public static MetadataDbDialect current(Environment environment) {
        return forType(MetadataDbTypeResolver.resolve(environment));
    }

    // 将显式元数据库类型映射为稳定的方言实现
    public static MetadataDbDialect forType(MetadataDbType type) {
        return OB_ORACLE;
    }

    // OceanBase Oracle 元库使用独立 SQL 片段，避免复用 MySQL 专有语法
    private static final class ObOracleDialect implements MetadataDbDialect {
        // 方言实例固定绑定 OceanBase Oracle 类型
        @Override
        public MetadataDbType type() {
            return MetadataDbType.OB_ORACLE;
        }

        // Oracle 兼容元库使用双引号保护大小写和保留字字段
        @Override
        public String quoteIdentifier(String identifier) {
            return "\"" + StringUtils.defaultString(identifier).replace("\"", "\"\"") + "\"";
        }

        // Oracle 兼容元库采用 OFFSET/FETCH 分页语法
        @Override
        public String limitOffset(String sql, long limit, long offset) {
            return sql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }

        // Oracle 兼容元库使用 NVL 处理空值回退
        @Override
        public String ifNull(String expression, String fallback) {
            return "NVL(" + expression + ", " + fallback + ")";
        }

        // Oracle 兼容元库使用 || 连接字符串表达式
        @Override
        public String concat(String... expressions) {
            return Arrays.stream(expressions).collect(Collectors.joining(" || "));
        }

        // Oracle 兼容元库使用会话时区时间，避免页面显示 UTC 时间
        @Override
        public String currentTimestamp() {
            return "CURRENT_TIMESTAMP";
        }

        @Override
        public String timestampParameter() {
            return "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS')";
        }

        @Override
        public Object timestampValue(String value) {
            return value;
        }

        // Oracle 兼容元库通过 LOWER 和 || 生成大小写不敏感 LIKE 条件
        @Override
        public String caseInsensitiveLike(String expression, String parameterExpression) {
            return "LOWER(" + expression + ") LIKE LOWER('%' || " + parameterExpression + " || '%')";
        }

        // Oracle 字符串常量只需要转义单引号，不追加 MySQL 排序规则
        @Override
        public String stringLiteral(String value) {
            return "'" + StringUtils.defaultString(value).replace("'", "''") + "'";
        }

        // Oracle 字段转字符串使用 TO_CHAR，避免 CAST 长度差异
        @Override
        public String stringCast(String expression) {
            return "TO_CHAR(" + expression + ")";
        }

        // Oracle 空字符串占位使用 VARCHAR2，保证 UNION 字段类型可推断
        @Override
        public String nullableString() {
            return "CAST(NULL AS VARCHAR2(255))";
        }

        // Oracle 兼容元库不支持 MySQL collation 片段，直接返回原表达式
        @Override
        public String collate(String expression) {
            return expression;
        }

        // Oracle 数值转换使用 TO_NUMBER，服务资源编号比较和排序
        @Override
        public String numberCast(String expression) {
            return "TO_NUMBER(" + expression + ")";
        }

        // Oracle 缺失插入必须带冲突条件，委托给 NOT EXISTS 版本
        @Override
        public String insertIgnore(String table, String columns, String values) {
            return mergeIfMissing(table, columns, values, null);
        }

        // Oracle 通过 INSERT SELECT WHERE NOT EXISTS 实现幂等插入
        @Override
        public String mergeIfMissing(String table, String columns, String values, String conflictPredicate) {
            if (StringUtils.isBlank(conflictPredicate)) {
                throw new IllegalArgumentException("OB Oracle merge requires a conflict predicate");
            }
            return "INSERT INTO " + table + " (" + columns + ") "
                    + "SELECT " + values + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM " + table
                    + " WHERE " + conflictPredicate + ")";
        }

        // Oracle 使用 MERGE 同时覆盖新增和更新，冲突条件由调用方按业务键提供
        @Override
        public String upsert(String table, String columns, String values, String conflictPredicate,
                             List<String> updateAssignments) {
            if (StringUtils.isBlank(conflictPredicate)) {
                throw new IllegalArgumentException("OB Oracle upsert requires a conflict predicate");
            }
            String updateSql = updateAssignments == null || updateAssignments.isEmpty()
                    ? ""
                    : " UPDATE SET " + String.join(", ", updateAssignments);
            return "MERGE INTO " + table + " target USING (SELECT " + values + " FROM DUAL) source ON ("
                    + conflictPredicate + ") WHEN MATCHED THEN" + updateSql
                    + " WHEN NOT MATCHED THEN INSERT (" + columns + ") VALUES (" + values + ")";
        }
    }
}
