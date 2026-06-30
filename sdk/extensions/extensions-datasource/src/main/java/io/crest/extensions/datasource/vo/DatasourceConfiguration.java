package io.crest.extensions.datasource.vo;

import lombok.Data;

import java.util.List;

@Data
/**
 * 数据源级配置，包含不支持参数和元数据查询 SQL
 */
public class DatasourceConfiguration extends Configuration {
    /**
     * 当前数据源类型需要拒绝的参数名称
     */
    private List<String> illegalParameters;
    /**
     * 当前数据源类型用于列出表的 SQL 模板
     */
    private List<String> showTableSqls;


    /**
     * 内置数据源类型元数据，用于驱动识别和标识符引用
     */
    static public enum DatasourceType {
        folder("folder", "folder", "folder", null, null, 25),
        API("API", "API", "API", "`", "`", 15),
        Excel("Excel", "Excel", "LOCALFILE", "`", "`", 16),
        ExcelRemote("ExcelRemote", "ExcelRemote", "LOCALFILE", "`", "`", 29),
        mysql("mysql", "Mysql", "OLTP", "`", "`", 27),
        obMysql("obMysql", "OceanBase MySQL", "OLTP", "`", "`", 32),
        impala("impala", "Apache Impala", "OLAP", "`", "`", 5),
        mariadb("mariadb", "Mariadb", "OLTP", "`", "`", 6),
        StarRocks("StarRocks", "StarRocks", "OLAP", "`", "`", 7),
        es("es", "Elasticsearch", "OLAP", "\"", "\"", 14),
        doris("doris", "Apache Doris", "OLAP", "`", "`", 26),
        TiDB("TiDB", "TiDB", "OLTP", "`", "`", 3),
        oracle("oracle", "ORACLE", "OLTP", "\"", "\"", 1),
        obOracle("obOracle", "OceanBase Oracle", "OLTP", "\"", "\"", 31),
        pg("pg", "PostgreSQL", "OLTP", "\"", "\"", 9),
        redshift("redshift", "AWS Redshift", "OLTP", "\"", "\"", 13),
        db2("db2", "Db2", "OLTP", "", "", 12),
        ck("ck", "Clickhouse", "OLAP", "`", "`", 11),
        h2("h2", "H2", "OLAP", "\"", "\"", 30),
        sqlServer("sqlServer", "Sqlserver", "DL", "[", "]", 2),
        mongo("mongo", "MongoDB", "DL", "`", "`", 10);

        /**
         * 内部数据源类型编码
         */
        private String type;
        /**
         * 数据源选择界面展示名称
         */
        private String name;
        /**
         * 数据源列表使用的稳定排序标记
         */
        private Integer flag;
        /**
         * 数据源所属目录分组
         */
        private String catalog;
        /**
         * 标识符引用前缀
         */
        private String prefix;
        /**
         * 标识符引用后缀
         */
        private String suffix;

        /**
         * 创建数据源类型元数据项
         */
        DatasourceType(String type, String name, String catalog, String prefix, String suffix, Integer flag) {
            this.type = type;
            this.name = name;
            this.catalog = catalog;
            this.prefix = prefix;
            this.suffix = suffix;
            this.flag = flag;
        }

        /**
         * 返回内部数据源类型编码
         */
        public String getType() {
            return type;
        }

        /**
         * 返回数据源展示名称
         */
        public String getName() {
            return name;
        }

        /**
         * 返回数据源目录分组
         */
        public String getCatalog() {
            return catalog;
        }

        /**
         * 返回标识符引用前缀
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * 返回标识符引用后缀
         */
        public String getSuffix() {
            return suffix;
        }

        /**
         * 返回稳定排序标记
         */
        public Integer getFlag() {
            return flag;
        }
    }
}
