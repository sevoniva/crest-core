package org.flywaydb.database.oracle;

import org.flywaydb.core.internal.jdbc.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;

/**
 * 避开 OceanBase Oracle 租户不可用的 Oracle 数据字典视图
 */
public class OceanBaseOracleSchema extends OracleSchema {

    OceanBaseOracleSchema(JdbcTemplate jdbcTemplate, OracleDatabase database, String name) {
        super(jdbcTemplate, database, name);
    }

    // 通过 USER_OBJECTS 判断当前 schema 是否为空，覆盖迁移前置校验
    @Override
    protected boolean doEmpty() throws SQLException {
        return jdbcTemplate.queryForInt("""
                SELECT COUNT(*)
                FROM USER_OBJECTS
                WHERE OBJECT_TYPE IN (
                    'TABLE', 'VIEW', 'SEQUENCE', 'TRIGGER', 'SYNONYM',
                    'PROCEDURE', 'FUNCTION', 'PACKAGE', 'PACKAGE BODY',
                    'TYPE', 'TYPE BODY', 'MATERIALIZED VIEW'
                )
                """) == 0;
    }

    // Flyway 只需要表对象清单，使用 OceanBase 支持的 USER_TABLES 获取
    @Override
    protected OracleTable[] doAllTables() throws SQLException {
        List<String> tableNames = jdbcTemplate.queryForStringList("""
                SELECT TABLE_NAME
                FROM USER_TABLES
                ORDER BY TABLE_NAME
                """);
        return tableNames.stream()
                .map(tableName -> new OracleTable(jdbcTemplate, database, this, tableName))
                .toArray(OracleTable[]::new);
    }
}
