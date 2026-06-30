package io.crest.startup.flyway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

final class OceanBaseFlywayDetector {

    private OceanBaseFlywayDetector() {
    }

    static boolean isOceanBase(String databaseProductName) {
        return databaseProductName != null
                && databaseProductName.toLowerCase(Locale.ROOT).contains("oceanbase");
    }

    static boolean isOracleTenant(Connection connection) {
        return canQuery(connection, "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL");
    }

    static boolean isMySqlTenant(Connection connection) {
        return canQuery(connection, "SELECT DATABASE()");
    }

    // 通过轻量探测 SQL 判断当前租户支持的方言能力
    private static boolean canQuery(Connection connection, String sql) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next();
        } catch (SQLException e) {
            return false;
        }
    }
}
