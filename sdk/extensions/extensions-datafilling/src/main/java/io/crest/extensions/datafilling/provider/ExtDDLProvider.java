package io.crest.extensions.datafilling.provider;


import io.crest.extensions.datafilling.dto.ExtIndexField;
import io.crest.extensions.datafilling.dto.ExtTableField;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.extensions.datasource.dto.TableFieldWithValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据填报 DDL 提供器，定义各类数据源的建表、改表和数据写入 SQL 生成能力
 */
public abstract class ExtDDLProvider {

    /**
     * 默认日期时间格式，供字段查询和写入 SQL 复用
     */
    public final String DEFAULT_DATE_FORMAT_STR = "yyyy-MM-dd HH:mm:ss";

    /**
     * 指示当前数据源是否支持在建表语句中直接携带注释
     */
    public boolean useCreateSqlWithComment() {
        return false;
    }

    /**
     * 生成带表注释和字段注释的建表 SQL
     */
    public String createTableSqlWithComment(String table, List<ExtTableField> formFields, String tableComment) throws Exception {
        throw new Exception("method not implemented");
    }

    /**
     * 生成不包含注释的建表 SQL
     */
    public String createTableSql(String table, List<ExtTableField> formFields) throws Exception {
        throw new Exception("method not implemented");
    }

    /**
     * 生成表注释和字段注释 SQL，供不支持内联注释的数据源使用
     */
    public List<String> createComment(String table, List<ExtTableField> formFields, String tableComment) {
        return new ArrayList<>();
    }

    /**
     * 生成读取表字段元数据的 SQL
     */
    @Deprecated
    public String getTableFieldsSql(String table) {
        String sql = "SELECT * FROM `$TABLE_NAME$` LIMIT 0 OFFSET 0";
        return sql.replace("$TABLE_NAME$", table);
    }

    /**
     * 生成新增或修改表字段的 SQL
     */
    public abstract String addTableColumnSql(String table, List<ExtTableField> formFieldsToCreate, List<ExtTableField> formFieldsToModify);

    /**
     * 生成删除表字段的 SQL
     */
    public abstract String dropTableColumnSql(String table, List<ExtTableField> formFields);

    /**
     * 生成带筛选和分页的旧版查询 SQL
     */
    @Deprecated
    public String searchSql(String table, List<TableField> formFields, String whereSql, long limit, long offset) {
        String baseSql = "SELECT $Column_Fields$ FROM `$TABLE_NAME$` $WHERE_SQL$ ;";
        if (limit > 0) {
            baseSql = "SELECT $Column_Fields$ FROM `$TABLE_NAME$` $WHERE_SQL$ LIMIT $OFFSET_COUNT$, $LIMIT_COUNT$ ;";
        }
        baseSql = baseSql.replace("$TABLE_NAME$", table)
                .replace("$OFFSET_COUNT$", Long.toString(offset))
                .replace("$LIMIT_COUNT$", Long.toString(limit));
        if (StringUtils.isBlank(whereSql)) {
            baseSql = baseSql.replace("$WHERE_SQL$", "");
        } else {
            baseSql = baseSql.replace("$WHERE_SQL$", whereSql);
        }
        baseSql = baseSql.replace("$Column_Fields$", convertSearchFields(formFields));
        return baseSql;
    }

    /**
     * 将查询字段转换为 SQL SELECT 字段片段
     */
    private String convertSearchFields(List<TableField> formFields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < formFields.size(); i++) {
            TableField f = formFields.get(i);
            if (Strings.CI.equalsAny(f.getType(), "datetime")) {
                // 日期时间字段统一格式化输出，避免不同数据库默认格式不一致
                builder.append("DATE_FORMAT(`").append(f.getOriginName()).append("`,'%Y-%m-%d %H:%i:%S')");
            } else {
                builder.append("`").append(f.getOriginName()).append("`");
            }
            if (i < formFields.size() - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    /**
     * 生成查询指定列去重值的旧版 SQL
     */
    @Deprecated
    public String searchColumnData(String table, String column, String order) {
        String baseSql = "SELECT DISTINCT `$Column_Field$` FROM `$TABLE_NAME$` ORDER BY `$Column_Field$` $Column_Order$;";
        baseSql = baseSql.replace("$TABLE_NAME$", table).replace("$Column_Field$", column).replace("$Column_Field$", column);
        if (Strings.CI.equals(order, "desc")) {
            baseSql = baseSql.replace("$Column_Order$", "DESC");
        } else {
            baseSql = baseSql.replace("$Column_Order$", "ASC");
        }
        return baseSql;
    }

    /**
     * 生成按指定字段值查询单行数据的旧版 SQL
     */
    @Deprecated
    public String searchColumnRowDataOne(String table, List<TableField> searchFields, TableFieldWithValue tableFieldWithValue) {
        String baseSql = "SELECT $Column_Fields$ FROM `$TABLE_NAME$` WHERE `$Column_Field$` = ? LIMIT 1;";
        baseSql = baseSql
                .replace("$Column_Fields$", StringUtils.join(searchFields.stream().map(s -> "`" + s.getOriginName() + "`").toList(), ", "))
                .replace("$TABLE_NAME$", table)
                .replace("$Column_Field$", tableFieldWithValue.getFiledName());
        return baseSql;
    }

    /**
     * 生成统计记录数的旧版 SQL
     */
    @Deprecated
    public String countSql(String table, String whereSql) {
        String baseSql = "SELECT COUNT(1) FROM `$TABLE_NAME$` $WHERE_SQL$ ;";
        baseSql = baseSql.replace("$TABLE_NAME$", table);
        if (StringUtils.isBlank(whereSql)) {
            baseSql = baseSql.replace("$WHERE_SQL$", "");
        } else {
            baseSql = baseSql.replace("$WHERE_SQL$", whereSql);
        }
        return baseSql;
    }

    /**
     * 生成删除数据表的 SQL
     */
    public abstract String dropTableSql(String table);

    /**
     * 生成创建索引的 SQL 列表
     */
    public abstract List<String> createTableIndexSql(String table, List<ExtIndexField> indexFields);

    /**
     * 生成删除索引的 SQL 列表
     */
    public abstract List<String> dropTableIndexSql(String table, List<ExtIndexField> indexFields);

    /**
     * 生成按主键集合删除数据的 SQL
     */
    public abstract String deleteDataByIdsSql(String table, List<TableFieldWithValue> pks);

    /**
     * 生成批量插入数据的 SQL
     */
    public abstract String insertDataSql(String tableName, List<TableFieldWithValue> fields, int count);

    /**
     * 生成按主键更新数据的 SQL
     */
    public abstract String updateDataByIdSql(String tableName, List<TableFieldWithValue> fields, TableFieldWithValue pk);

    /**
     * 生成检查字段唯一性的旧版 SQL
     */
    @Deprecated
    public String checkUniqueValueSql(String tableName, TableFieldWithValue field, TableFieldWithValue pk) {
        String sql = "SELECT COUNT(1) FROM `$TABLE_NAME$` WHERE `$Column_Field$` = ? $PRIMARY_KEY_CONDITION$;";

        StringBuilder pkCondition = new StringBuilder();
        if (pk != null) {
            pkCondition.append("AND `").append(pk.getFiledName()).append("` != ?");
        }

        return sql.replace("$TABLE_NAME$", tableName)
                .replace("$Column_Field$", field.getFiledName())
                .replace("$PRIMARY_KEY_CONDITION$", pkCondition.toString());
    }

    /**
     * 根据查询字段生成旧版 WHERE 条件片段
     */
    @Deprecated
    public String whereSql(String tableName, List<TableField> searchFields) {
        StringBuilder builder = new StringBuilder("WHERE 1 = 1 ");
        for (TableField searchField : searchFields) {
            if (searchField.getInCount() > 1) {
                List<String> pList = new ArrayList<>();
                for (int i = 0; i < searchField.getInCount(); i++) {
                    pList.add("?");
                }
                String str = "AND $Column_Field$ IN (" + String.join(", ", pList) + ")";
                builder.append(str.replace("$Column_Field$", searchField.getOriginName()));
            } else {
                switch (searchField.getTerm()) {
                    case "not_eq":
                        builder.append(("AND $Column_Field$ " + "!=" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                    case "lt":
                        builder.append(("AND $Column_Field$ " + "<" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                    case "gt":
                        builder.append(("AND $Column_Field$ " + ">" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                    case "le":
                        builder.append(("AND $Column_Field$ " + "<=" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                    case "ge":
                        builder.append(("AND $Column_Field$ " + ">=" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                    case "null":
                        builder.append("AND $Column_Field$ IS NULL ");
                        break;
                    case "not_null":
                        builder.append("AND $Column_Field$ IS NOT NULL ");
                        break;
                    default:
                        builder.append(("AND $Column_Field$ " + "=" + " ? ").replace("$Column_Field$", searchField.getOriginName()));
                        break;
                }

            }
        }
        return builder.toString();
    }

    /**
     * 生成读取数据库表名大小写配置的 SQL
     */
    @Deprecated
    public String getLowerCaseTaleNames() {
        return "SHOW VARIABLES LIKE 'lower_case_table_names'";
    }

    /**
     * 将字段类型名称映射为数据源内部列类型
     */
    public abstract Integer getColumnType(String name);

    /**
     * 生成清空数据表的 SQL
     */
    public abstract String truncateTable(String table);

    /**
     * 生成查询全部主键值的 SQL
     */
    public abstract String listAllIds(String table, String keyColumn);

}
