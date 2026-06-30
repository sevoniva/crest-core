package io.crest.dataset.utils;

import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.DsTypeDTO;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.utils.Md5Utils;
import org.apache.calcite.avatica.util.Quoting;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据表和字段命名工具
 */
public class TableUtils {

    public static String format = Quoting.BACK_TICK.string + "%s" + Quoting.BACK_TICK.string;

    /**
     * 返回原始表名
     */
    public static String tableName(String name) {
        return name;
    }

    /**
     * 生成临时表名
     */
    public static String tmpName(String name) {
        return "tmp_" + name;
    }

    /**
     * 生成删除标记表名
     */
    public static String deleteName(String dorisName) {
        return "delete_" + dorisName;
    }

    /**
     * 生成新增标记表名
     */
    public static String addName(String dorisName) {
        return "add_" + dorisName;
    }

    /**
     * 根据 Doris 字段名生成内部字段名
     */
    public static String fieldName(String dorisName) {
        return "f_" + Md5Utils.md5(dorisName);
    }

    /**
     * 根据 Doris 字段名生成较短的内部字段名
     */
    public static String fieldNameShort(String dorisName) {
        return "f_" + Md5Utils.md5(dorisName).substring(8, 24);
    }

    /**
     * 根据字段名生成物理列名
     */
    public static String columnName(String fieldName) {
        return "C_" + Md5Utils.md5(fieldName);
    }

    /**
     * 生成带 schema 和别名的数据表 SQL 片段
     */
    public static String getTableAndAlias(SQLObj sqlObj, DsTypeDTO datasourceType, boolean isCross) {
        String schema = "";
        String prefix = "";
        String suffix = "";
        if (StringUtils.isNotEmpty(sqlObj.getTableSchema())) {
            if (isCross) {
                prefix = "`";
                suffix = "`";
            } else {
                prefix = datasourceType.getPrefix();
                suffix = datasourceType.getSuffix();
            }
            schema = prefix + sqlObj.getTableSchema() + suffix + ".";
        }
        return schema + prefix + sqlObj.getTableName() + suffix + " " + sqlObj.getTableAlias();
    }

    /**
     * 生成查询指定数据表的 SQL
     */
    public static String tableName2Sql(DatasourceSchemaDTO ds, String tableName) {
        return "SELECT * FROM " + ds.getSchemaAlias() + "." + String.format(format, tableName);
    }
}
