package io.crest.engine.trans;

import io.crest.constant.SQLConstants;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.model.SQLObj;

// 生成 SQL 模板使用的主表描述，并保留自定义 SQL 片段的方言占位符
public class Table2SQLObj {

    // 自定义 SQL 在单源查询中延后交给方言转换，普通表直接拼接引用名
    public static void table2sqlobj(SQLMeta meta, String tablePrefix, String table, boolean crossDs) {
        String sql;
        if (table.startsWith("(") && table.endsWith(")") && !crossDs) {// SQL片段和关联
            meta.setTableDialect(table.substring(1, table.length() - 1));
            sql = "(" + SqlPlaceholderConstants.TABLE_PLACEHOLDER + ")";
        } else {
            sql = table;
        }
        SQLObj tableObj = SQLObj.builder()
                .tableName((table.startsWith("(") && table.endsWith(")")) ? sql : String.format(SQLConstants.TABLE_NAME, tablePrefix, table))
                .tableAlias(String.format(SQLConstants.TABLE_ALIAS_PREFIX, 0))
                .build();
        meta.setTable(tableObj);
    }
}
