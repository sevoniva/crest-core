package io.crest.interceptor;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
/**
 * 为 OB Oracle 元数据库中已知的保留字段补充引号，兼容 MyBatis Plus 生成的通用 SQL
 */
public class ObOracleReservedIdentifierInterceptor implements Interceptor {

    private static final Map<String, List<String>> RESERVED_COLUMNS_BY_TABLE = Map.ofEntries(
            Map.entry("core_dataset", List.of("level", "mode")),
            Map.entry("core_dataset_field", List.of("size")),
            Map.entry("core_workspace_recent_resource", List.of("uid", "time")),
            Map.entry("core_workspace_favorite_resource", List.of("uid", "time")),
            Map.entry("core_font_asset", List.of("size")),
            Map.entry("core_iam_user_org", List.of("uid")),
            Map.entry("core_iam_user_role", List.of("uid")),
            Map.entry("core_iam_org", List.of("level")),
            Map.entry("core_reference_area", List.of("level")),
            Map.entry("core_visualization", List.of("level")),
            Map.entry("core_visualization_snapshot", List.of("level")),
            Map.entry("core_template", List.of("level")),
            Map.entry("core_template_category", List.of("level"))
    );

    private final boolean enabled;

    public ObOracleReservedIdentifierInterceptor(boolean enabled) {
        this.enabled = enabled;
    }

    // 在 Statement 预编译前改写 SQL，确保分页和参数绑定仍由 MyBatis 原流程处理
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (enabled && invocation.getTarget() instanceof StatementHandler statementHandler) {
            BoundSql boundSql = statementHandler.getBoundSql();
            String sql = boundSql.getSql();
            String rewrittenSql = rewrite(sql);
            if (!sql.equals(rewrittenSql)) {
                setSql(statementHandler, rewrittenSql);
            }
        }
        return invocation.proceed();
    }

    // 仅包装 MyBatis 支持的目标对象，避免影响非 StatementHandler 插件链
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    // 当前拦截器由 Spring 构造，MyBatis XML 属性入口无需额外处理
    @Override
    public void setProperties(Properties properties) {
    }

    // 先归一化 MySQL 写法，再按涉及表名补充保留字段引号
    private String rewrite(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        String rewrittenSql = normalizeMysqlSql(rewriteMysqlFunctions(sql));
        String lowerSql = rewrittenSql.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : RESERVED_COLUMNS_BY_TABLE.entrySet()) {
            if (!lowerSql.contains(entry.getKey())) {
                continue;
            }
            for (String column : entry.getValue()) {
                rewrittenSql = quoteIdentifier(rewrittenSql, column);
            }
        }
        return rewrittenSql;
    }

    // 将反引号标识符转换为 Oracle 可执行形式，字符串字面量保持原样
    private String normalizeMysqlSql(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"') {
                int quotedEnd = consumeQuotedSegment(sql, index, current);
                result.append(sql, index, quotedEnd + 1);
                index = quotedEnd;
                continue;
            }
            if (current == '`') {
                int quotedEnd = consumeQuotedSegment(sql, index, current);
                result.append(normalizeIdentifier(sql.substring(index + 1, quotedEnd)));
                index = quotedEnd;
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    // 简单标识符去掉 MySQL 反引号，复杂标识符转为 Oracle 双引号
    private String normalizeIdentifier(String identifier) {
        String value = identifier.replace("``", "`");
        if (value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"").toUpperCase(Locale.ROOT) + "\"";
    }

    // 替换元数据 SQL 中常见 MySQL 函数，避开字符串和已引用片段
    private String rewriteMysqlFunctions(String sql) {
        String rewrittenSql = rewriteOutsideQuotedSegments(sql,
                Pattern.compile("(?i)\\buuid\\s*\\(\\s*\\)"),
                "LOWER(RAWTOHEX(SYS_GUID()))");
        return rewriteOutsideQuotedSegments(rewrittenSql,
                Pattern.compile("(?i)\\bifnull\\s*\\("),
                "NVL(");
    }

    // 对未引用的保留字段补双引号，避免重复处理已有引号或反引号
    private String quoteIdentifier(String sql, String identifier) {
        Pattern pattern = Pattern.compile("(?i)(?<![\"`])\\b" + Pattern.quote(identifier) + "\\b(?![\"`])");
        String replacement = "\"" + identifier.toUpperCase(Locale.ROOT) + "\"";
        StringBuilder result = new StringBuilder(sql.length());
        int segmentStart = 0;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (!isSqlQuote(current)) {
                continue;
            }
            result.append(quoteSegment(sql.substring(segmentStart, index), pattern, replacement));
            int quotedEnd = consumeQuotedSegment(sql, index, current);
            result.append(sql, index, quotedEnd + 1);
            index = quotedEnd;
            segmentStart = quotedEnd + 1;
        }
        result.append(quoteSegment(sql.substring(segmentStart), pattern, replacement));
        return result.toString();
    }

    // SQL 字符串、双引号标识符和反引号标识符都按引用片段处理
    private boolean isSqlQuote(char value) {
        return value == '\'' || value == '"' || value == '`';
    }

    // 扫描引用片段终点，兼容 SQL 中连续两个引号表示转义的写法
    private int consumeQuotedSegment(String sql, int start, char quote) {
        for (int index = start + 1; index < sql.length(); index++) {
            if (sql.charAt(index) != quote) {
                continue;
            }
            if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                index++;
                continue;
            }
            return index;
        }
        return sql.length() - 1;
    }

    // 对单个非引用片段执行正则替换，统一处理替换文本中的特殊字符
    private String quoteSegment(String sql, Pattern pattern, String replacement) {
        return pattern.matcher(sql).replaceAll(Matcher.quoteReplacement(replacement));
    }

    // 只改写引用片段之外的内容，避免破坏 SQL 字符串和标识符原文
    private String rewriteOutsideQuotedSegments(String sql, Pattern pattern, String replacement) {
        StringBuilder result = new StringBuilder(sql.length());
        int segmentStart = 0;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (!isSqlQuote(current)) {
                continue;
            }
            result.append(quoteSegment(sql.substring(segmentStart, index), pattern, replacement));
            int quotedEnd = consumeQuotedSegment(sql, index, current);
            result.append(sql, index, quotedEnd + 1);
            index = quotedEnd;
            segmentStart = quotedEnd + 1;
        }
        result.append(quoteSegment(sql.substring(segmentStart), pattern, replacement));
        return result.toString();
    }

    // 兼容 MyBatis 不同 StatementHandler 包装层级下的 BoundSql 路径
    private void setSql(StatementHandler statementHandler, String sql) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        try {
            metaObject.setValue("delegate.boundSql.sql", sql);
        } catch (ReflectionException ignored) {
            metaObject.setValue("boundSql.sql", sql);
        }
    }
}
