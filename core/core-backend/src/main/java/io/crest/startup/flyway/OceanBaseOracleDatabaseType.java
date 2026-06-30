package io.crest.startup.flyway;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.database.oracle.OracleDatabaseType;

import java.sql.Connection;
import java.sql.Types;
import java.util.Properties;

/**
 * 让 Flyway 以 Oracle 兼容路径执行 OceanBase Oracle 租户迁移
 */
public class OceanBaseOracleDatabaseType extends OracleDatabaseType {

    // Flyway 日志和调试输出中展示的数据库类型名称
    @Override
    public String getName() {
        return "OceanBase Oracle";
    }

    // OceanBase Oracle 对 NULL 类型推断更保守，使用 VARCHAR 作为通用占位
    @Override
    public int getNullType() {
        return Types.VARCHAR;
    }

    // OceanBase Oracle 与 MySQL 租户共享 JDBC 协议前缀，后续通过连接探测区分
    @Override
    public boolean handlesJDBCUrl(String url) {
        return isOceanBaseUrl(url);
    }

    // OceanBase Oracle 元库必须使用 OceanBase 官方 JDBC 驱动
    @Override
    public String getDriverClass(String url, ClassLoader classLoader) {
        return "com.oceanbase.jdbc.Driver";
    }

    // 连接建立后通过 Oracle 租户可执行的探测语句确认兼容模式
    @Override
    public boolean handlesDatabaseProductNameAndVersion(String databaseProductName, String databaseProductVersion,
                                                        Connection connection) {
        return OceanBaseFlywayDetector.isOceanBase(databaseProductName)
                && OceanBaseFlywayDetector.isOracleTenant(connection);
    }

    // 返回定制数据库实现，绕过 Oracle 版本号和数据字典差异
    @Override
    public Database createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
                                   StatementInterceptor statementInterceptor) {
        return new OceanBaseOracleDatabase(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    // OceanBase JDBC 驱动不暴露 Oracle 钱包等默认连接属性
    @Override
    public void setDefaultConnectionProps(String url, Properties props, ClassLoader classLoader) {
    }

    // Oracle Wallet 和 Kerberos 配置不适用于 OceanBase JDBC 连接
    @Override
    public void setConfigConnectionProps(Configuration configuration, Properties props, ClassLoader classLoader) {
    }

    // 保留原始连接，避免父类对 Oracle 专有会话属性做额外修改
    @Override
    public Connection alterConnectionAsNeeded(Connection connection, Configuration configuration) {
        return connection;
    }

    // 优先级略高于默认 Oracle 类型，确保 jdbc:oceanbase 前缀由本类型接管
    @Override
    public int getPriority() {
        return super.getPriority() + 1;
    }

    // OceanBase 驱动使用独立 JDBC scheme
    private boolean isOceanBaseUrl(String url) {
        return url != null && url.startsWith("jdbc:oceanbase:");
    }
}
