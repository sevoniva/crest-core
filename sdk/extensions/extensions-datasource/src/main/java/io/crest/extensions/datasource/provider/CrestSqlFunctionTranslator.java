package io.crest.extensions.datasource.provider;

import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 将内置日期函数翻译为指定数据源可执行的 SQL 函数
public final class CrestSqlFunctionTranslator {
    private static final List<String> DATE_FUNCTIONS = List.of(
            "CREST_CAST_DATE_FORMAT",
            "CREST_UNIX_TIMESTAMP",
            "CREST_STR_TO_DATE",
            "CREST_FROM_UNIXTIME",
            "CREST_DATE_FORMAT"
    );
    private static final Set<String> MYSQL_FAMILY = Set.of(
            "mysql",
            "mongo",
            "mariadb",
            "tidb",
            "starrocks",
            "doris"
    );

    private CrestSqlFunctionTranslator() {
    }

    // 按数据源类型翻译 SQL 中的内置日期函数
    public static String translate(String sql, DatasourceSchemaDTO datasource) {
        if (StringUtils.isBlank(sql) || datasource == null || !isMysqlFamily(datasource.getType())) {
            return sql;
        }
        return rewriteMysql(sql);
    }

    // 判断数据源类型是否属于 MySQL 兼容语法族
    private static boolean isMysqlFamily(String type) {
        if (StringUtils.isBlank(type)) {
            return false;
        }
        return MYSQL_FAMILY.stream().anyMatch(item -> Strings.CI.equals(item, type));
    }

    // 重写 MySQL 兼容 SQL 中的内置日期函数调用
    private static String rewriteMysql(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'') {
                index = appendQuoted(sql, index, result);
                continue;
            }
            FunctionMatch functionMatch = matchFunctionName(sql, index);
            if (functionMatch != null) {
                int openParen = skipWhitespace(sql, functionMatch.endIndex());
                int closeParen = findClosingParen(sql, openParen);
                if (closeParen > openParen) {
                    List<String> args = splitArguments(sql.substring(openParen + 1, closeParen));
                    String replacement = rewriteMysqlFunction(functionMatch.name(), args);
                    if (replacement != null) {
                        removeTrailingQualifier(sql, index, result);
                        result.append(replacement);
                        index = closeParen + 1;
                        continue;
                    }
                }
            }
            result.append(current);
            index++;
        }
        return result.toString();
    }

    // 移除函数名前的 schema 或反引号限定符
    private static void removeTrailingQualifier(String sql, int functionStart, StringBuilder result) {
        if (functionStart < 2 || sql.charAt(functionStart - 1) != '.') {
            return;
        }
        int qualifierStart = findQualifierStart(sql, functionStart - 2);
        if (qualifierStart < 0) {
            return;
        }
        int qualifierLength = functionStart - qualifierStart;
        if (result.length() >= qualifierLength) {
            result.setLength(result.length() - qualifierLength);
        }
    }

    // 定位函数限定符的起始位置
    private static int findQualifierStart(String sql, int end) {
        if (sql.charAt(end) == '`') {
            int start = sql.lastIndexOf('`', end - 1);
            return start >= 0 ? start : -1;
        }
        int index = end;
        while (index >= 0 && isIdentifierChar(sql.charAt(index))) {
            index--;
        }
        return index + 1 <= end ? index + 1 : -1;
    }

    // 将单个内置日期函数转换为 MySQL 兼容函数
    private static String rewriteMysqlFunction(String functionName, List<String> args) {
        String normalized = functionName.toUpperCase();
        if (Strings.CS.equals(normalized, "CREST_STR_TO_DATE") && args.size() >= 2) {
            return "STR_TO_DATE(" + rewriteMysql(args.get(0).trim()) + ", " + mysqlFormat(args.get(1)) + ")";
        }
        if (Strings.CS.equals(normalized, "CREST_DATE_FORMAT") && args.size() >= 2) {
            return "DATE_FORMAT(" + rewriteMysql(args.get(0).trim()) + ", " + mysqlFormat(args.get(1)) + ")";
        }
        if (Strings.CS.equals(normalized, "CREST_CAST_DATE_FORMAT") && args.size() >= 3) {
            return "DATE_FORMAT(" + rewriteMysql(args.get(0).trim()) + ", " + mysqlFormat(args.get(2)) + ")";
        }
        if (Strings.CS.equals(normalized, "CREST_FROM_UNIXTIME") && !args.isEmpty()) {
            if (args.size() >= 2) {
                return "FROM_UNIXTIME(" + rewriteMysql(args.get(0).trim()) + ", " + mysqlFormat(args.get(1)) + ")";
            }
            return "FROM_UNIXTIME(" + rewriteMysql(args.get(0).trim()) + ")";
        }
        if (Strings.CS.equals(normalized, "CREST_UNIX_TIMESTAMP") && !args.isEmpty()) {
            return "UNIX_TIMESTAMP(" + rewriteMysql(args.get(0).trim()) + ")";
        }
        return null;
    }

    // 从当前位置匹配支持翻译的函数名
    private static FunctionMatch matchFunctionName(String sql, int start) {
        for (String functionName : DATE_FUNCTIONS) {
            FunctionMatch match = matchUnquotedFunctionName(sql, start, functionName);
            if (match != null) {
                return match;
            }
            match = matchBacktickQuotedFunctionName(sql, start, functionName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    // 匹配未加反引号的函数名
    private static FunctionMatch matchUnquotedFunctionName(String sql, int start, String functionName) {
        if (start > 0 && isIdentifierChar(sql.charAt(start - 1))) {
            return null;
        }
        int end = start + functionName.length();
        if (end > sql.length() || !sql.regionMatches(true, start, functionName, 0, functionName.length())) {
            return null;
        }
        int next = skipWhitespace(sql, end);
        return next < sql.length() && sql.charAt(next) == '(' ? new FunctionMatch(functionName, end) : null;
    }

    // 匹配 Calcite 生成的反引号函数名
    private static FunctionMatch matchBacktickQuotedFunctionName(String sql, int start, String functionName) {
        if (sql.charAt(start) != '`') {
            return null;
        }
        int end = start + functionName.length() + 2;
        if (end > sql.length() || sql.charAt(end - 1) != '`') {
            return null;
        }
        if (!sql.regionMatches(true, start + 1, functionName, 0, functionName.length())) {
            return null;
        }
        int next = skipWhitespace(sql, end);
        return next < sql.length() && sql.charAt(next) == '(' ? new FunctionMatch(functionName, end) : null;
    }

    // 跳过当前位置后的空白字符
    private static int skipWhitespace(String sql, int start) {
        int index = start;
        while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
            index++;
        }
        return index;
    }

    // 原样追加 SQL 字符串字面量
    private static int appendQuoted(String sql, int start, StringBuilder result) {
        result.append(sql.charAt(start));
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            result.append(current);
            index++;
            if (current == '\'') {
                if (index < sql.length() && sql.charAt(index) == '\'') {
                    result.append(sql.charAt(index));
                    index++;
                    continue;
                }
                break;
            }
        }
        return index;
    }

    // 跳过 SQL 字符串字面量
    private static int skipQuoted(String sql, int start) {
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            index++;
            if (current == '\'') {
                if (index < sql.length() && sql.charAt(index) == '\'') {
                    index++;
                    continue;
                }
                break;
            }
        }
        return index;
    }

    // 查找与起始括号匹配的结束括号
    private static int findClosingParen(String sql, int openParen) {
        if (openParen >= sql.length() || sql.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        int index = openParen;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'') {
                index = skipQuoted(sql, index);
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
            index++;
        }
        return -1;
    }

    // 按顶层逗号拆分函数参数
    private static List<String> splitArguments(String text) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\'') {
                index = skipQuoted(text, index);
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                args.add(text.substring(start, index));
                start = index + 1;
            }
            index++;
        }
        args.add(text.substring(start));
        return args;
    }

    // 将日期格式参数转换为 MySQL 格式字面量
    private static String mysqlFormat(String sqlFormat) {
        return "'" + javaDateFormatToMysql(stripSqlLiteral(sqlFormat)).replace("'", "''") + "'";
    }

    // 去掉 SQL 字符串字面量外层引号
    private static String stripSqlLiteral(String value) {
        String text = StringUtils.trimToEmpty(value);
        if (text.length() >= 2 && text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\'') {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        return text;
    }

    // 将 Java 风格日期格式转换为 MySQL 日期格式
    private static String javaDateFormatToMysql(String format) {
        if (Strings.CS.contains(format, "%")) {
            return format;
        }
        return format
                .replace("yyyy", "%Y")
                .replace("YYYY", "%Y")
                .replace("yy", "%y")
                .replace("YY", "%y")
                .replace("MM", "%m")
                .replace("dd", "%d")
                .replace("DD", "%d")
                .replace("HH", "%H")
                .replace("hh", "%h")
                .replace("mm", "%i")
                .replace("ss", "%s")
                .replace("SS", "%s");
    }

    // 判断字符是否可作为函数标识符的一部分
    private static boolean isIdentifierChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    // 保存匹配到的函数名和结束位置
    private record FunctionMatch(String name, int endIndex) {
    }
}
