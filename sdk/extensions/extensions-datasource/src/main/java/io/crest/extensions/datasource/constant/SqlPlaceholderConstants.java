package io.crest.extensions.datasource.constant;

public class SqlPlaceholderConstants {
    public static final String TABLE_PLACEHOLDER = "SELECT * FROM CREST_PLACEHOLDER_TABLE_0";

    public static final String KEYWORD_PREFIX_REGEX = "[`'\"\\[]?";

    public static final String KEYWORD_SUFFIX_REGEX = "[`'\"\\]]?";

    public static final String TABLE_PLACEHOLDER_REGEX = "SELECT \\* FROM " + KEYWORD_PREFIX_REGEX + "CREST_PLACEHOLDER_TABLE_0" + KEYWORD_SUFFIX_REGEX;

    public static final String CALC_FIELD_PLACEHOLDER = "CREST_CALC_FIELD_PLACEHOLDER_%s";
}
