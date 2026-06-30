package io.crest.extensions.datasource.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 提供当前模块复用的工具能力
public final class SqlPlaceholderUtils {

    private static final String IDENTIFIER_CHARACTERS = "A-Za-z0-9_$";

    private SqlPlaceholderUtils() {
    }

    public static String replaceIdentifier(String sql, String identifier, String replacement) {
        return identifierPattern(identifier, "").matcher(sql).replaceAll(Matcher.quoteReplacement(replacement));
    }

    public static String removeIdentifierPrefix(String sql, String identifier) {
        return identifierPattern(identifier, "\\.").matcher(sql).replaceAll("");
    }

    private static Pattern identifierPattern(String identifier, String suffix) {
        String quoted = Pattern.quote(identifier);
        String pattern = "(?:`" + quoted + "`|'" + quoted + "'|\"" + quoted + "\"|\\[" + quoted
                + "\\]|(?<![" + IDENTIFIER_CHARACTERS + "])" + quoted + "(?![" + IDENTIFIER_CHARACTERS + "]))"
                + suffix;
        return Pattern.compile(pattern);
    }
}
