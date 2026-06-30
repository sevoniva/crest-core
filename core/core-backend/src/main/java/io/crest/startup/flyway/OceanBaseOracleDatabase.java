package io.crest.startup.flyway;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.database.oracle.OracleDatabase;
import org.flywaydb.database.oracle.OracleConnection;
import org.flywaydb.database.oracle.OceanBaseOracleConnection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 适配 OceanBase Oracle 租户的 Flyway 数据库实现
 */
public class OceanBaseOracleDatabase extends OracleDatabase {

    public OceanBaseOracleDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
                                   StatementInterceptor statementInterceptor) {
        super(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    // OceanBase 返回自身版本号，跳过 Flyway 内置 Oracle 版本校验
    @Override
    public void ensureSupported(Configuration configuration) {
        // OceanBase Oracle 兼容性由 Crest 集成测试覆盖
    }

    // 使用自定义连接对象避开 OceanBase 缺失的 Oracle 字典视图
    @Override
    protected OracleConnection doGetConnection(Connection connection) {
        return new OceanBaseOracleConnection(this, connection);
    }

    // 读取当前租户数据库名，供 Flyway 记录迁移上下文
    @Override
    protected String doGetCatalog() throws SQLException {
        return jdbcTemplate.queryForString("SELECT SYS_CONTEXT('USERENV','DB_NAME') FROM DUAL");
    }

    // 读取当前执行用户，保持 Flyway schema 解析与租户上下文一致
    @Override
    protected String doGetCurrentUser() throws SQLException {
        return jdbcTemplate.queryForString("SELECT USER FROM DUAL");
    }
}
