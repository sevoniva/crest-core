package io.crest.extensions.datasource.provider;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 包装 JDBC Driver 以适配驱动注册和代理调用
 */
public class DriverShim implements Driver {
    private Driver driver;

    public DriverShim(Driver d) {
        this.driver = d;
    }

    /**
     * 判断底层驱动是否接受指定连接地址
     */
    @Override
    public boolean acceptsURL(String u) throws SQLException {
        return this.driver.acceptsURL(u);
    }

    @Override
    /**
     * 返回驱动属性信息
     */
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    /**
     * 返回驱动主版本号
     */
    public int getMajorVersion() {
        return 0;
    }

    @Override
    /**
     * 返回驱动次版本号
     */
    public int getMinorVersion() {
        return 0;
    }

    @Override
    /**
     * 判断驱动是否符合 JDBC 规范
     */
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    /**
     * 返回驱动父级日志记录器
     */
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    /**
     * 使用底层驱动创建数据库连接
     */
    @Override
    public Connection connect(String u, Properties p) throws SQLException {
        return this.driver.connect(u, p);
    }
}
