package org.flywaydb.database.oracle;

import java.sql.Connection;

/**
 * 保留 Flyway Oracle 迁移行为，同时切换到 OceanBase 可用的 schema 元数据实现
 */
public class OceanBaseOracleConnection extends OracleConnection {

    public OceanBaseOracleConnection(OracleDatabase database, Connection connection) {
        super(database, connection);
    }

    // 返回 OceanBase 专用 schema，避免访问不兼容的 Oracle 数据字典
    @Override
    public org.flywaydb.core.internal.database.base.Schema getSchema(String name) {
        return new OceanBaseOracleSchema(jdbcTemplate, database, name);
    }
}
