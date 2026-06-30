package io.crest.datasource.security;

import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 在用户 SQL 进入 Calcite 或 JDBC 连接前执行最后一层进程内安全检查
 * 这里只做词法级边界判断；数据源权限、参数绑定和方言校验仍由调用方负责
 */
public final class SqlSecurityPolicy {

    /**
     * 工具类不保存请求上下文，所有检查都必须保持无状态
     */
    private SqlSecurityPolicy() {
    }

    /**
     * 校验只读查询入口。允许的前缀覆盖数据预览和字段发现使用的查询、元数据查看命令
     *
     * @param sql 请求中的原始 SQL 文本
     * @return 原始 SQL 文本，保留调用方已有格式
     */
    public static String validateReadQuery(String sql) {
        String normalized = validateSingleStatement(sql);
        String lower = normalized.stripLeading().toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select")
                || lower.startsWith("with")
                || lower.startsWith("show")
                || lower.startsWith("desc")
                || lower.startsWith("describe")
                || lower.startsWith("explain"))) {
            CrestException.throwException("Only read-only SQL is allowed in this context");
        }
        return sql;
    }

    /**
     * 规范化 SQL，并拒绝会改变语句边界的输入。字符串、引用标识符和注释中的分号不视为新语句
     *
     * @param sql 请求中的原始 SQL 文本
     * @return 用于后续安全判断的规范化 SQL 文本
     */
    public static String validateSingleStatement(String sql) {
        if (StringUtils.isBlank(sql)) {
            CrestException.throwException("SQL must not be empty");
        }
        String normalized = Normalizer.normalize(sql, Normalizer.Form.NFKC).trim();
        if (normalized.indexOf('\0') >= 0 || containsControlCharacter(normalized)) {
            CrestException.throwException("SQL contains illegal characters");
        }
        if (containsExecutableSqlAfterTopLevelSemicolon(normalized)) {
            CrestException.throwException("Multiple SQL statements are not allowed");
        }
        return normalized;
    }

    /**
     * 判断顶层分号之后是否还有可执行内容。末尾分号后只有空白或注释时，按单语句兼容处理
     */
    private static boolean containsExecutableSqlAfterTopLevelSemicolon(String sql) {
        int semicolonIndex = findTopLevelSemicolon(sql, 0);
        if (semicolonIndex < 0) {
            return false;
        }
        int nextSemicolonIndex = findTopLevelSemicolon(sql, semicolonIndex + 1);
        if (nextSemicolonIndex >= 0) {
            return true;
        }
        return hasExecutableContent(sql, semicolonIndex + 1);
    }

    /**
     * 查找不在引号和注释内部的分号。这里不是完整 SQL 解析器，只跟踪会隐藏分号的常见结构
     */
    private static int findTopLevelSemicolon(String sql, int start) {
        char quote = 0;
        for (int i = start; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (quote != 0) {
                if (current == '\\' && quote != '`') {
                    i++;
                } else if (current == quote) {
                    if (next == quote && quote != '`') {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '-' && next == '-') {
                i = skipLineComment(sql, i + 2);
                continue;
            }
            if (current == '#') {
                i = skipLineComment(sql, i + 1);
                continue;
            }
            if (current == '/' && next == '*') {
                i = skipBlockComment(sql, i + 2);
                continue;
            }
            if (current == ';') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检查分号后的剩余文本是否包含可执行内容，空白和注释会被忽略
     */
    private static boolean hasExecutableContent(String sql, int start) {
        for (int i = start; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current == '-' && next == '-') {
                i = skipLineComment(sql, i + 2);
                continue;
            }
            if (current == '#') {
                i = skipLineComment(sql, i + 1);
                continue;
            }
            if (current == '/' && next == '*') {
                i = skipBlockComment(sql, i + 2);
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 跳过行注释，避免注释中的分号被误判为语句分隔符
     */
    private static int skipLineComment(String sql, int start) {
        int lineEnd = sql.indexOf('\n', start);
        return lineEnd < 0 ? sql.length() : lineEnd;
    }

    /**
     * 跳过块注释。未闭合块注释会直接消耗到 SQL 末尾，避免暴露后续伪造语句
     */
    private static int skipBlockComment(String sql, int start) {
        int blockEnd = sql.indexOf("*/", start);
        return blockEnd < 0 ? sql.length() : blockEnd + 1;
    }

    /**
     * 拒绝可能干扰日志、预览结果或驱动行为的控制字符；标准 SQL 空白字符仍然允许
     */
    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isISOControl(current) && current != '\n' && current != '\r' && current != '\t') {
                return true;
            }
        }
        return false;
    }
}
