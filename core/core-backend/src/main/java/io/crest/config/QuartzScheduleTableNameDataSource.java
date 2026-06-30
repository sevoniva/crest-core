package io.crest.config;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * 将 Quartz JDBC JobStore 生成的固定表名映射到 Crest 元数据表命名。
 */
final class QuartzScheduleTableNameDataSource extends AbstractDataSource {

    private static final Map<String, String> TABLE_NAMES = Map.ofEntries(
            Map.entry("core_schedule_BLOB_TRIGGERS", "core_schedule_blob_triggers"),
            Map.entry("core_schedule_CALENDARS", "core_schedule_calendars"),
            Map.entry("core_schedule_CRON_TRIGGERS", "core_schedule_cron_triggers"),
            Map.entry("core_schedule_FIRED_TRIGGERS", "core_schedule_fired_triggers"),
            Map.entry("core_schedule_JOB_DETAILS", "core_schedule_job_details"),
            Map.entry("core_schedule_LOCKS", "core_schedule_locks"),
            Map.entry("core_schedule_PAUSED_TRIGGER_GRPS", "core_schedule_paused_trigger_groups"),
            Map.entry("core_schedule_SCHEDULER_STATE", "core_schedule_scheduler_state"),
            Map.entry("core_schedule_SIMPLE_TRIGGERS", "core_schedule_simple_triggers"),
            Map.entry("core_schedule_SIMPROP_TRIGGERS", "core_schedule_simprop_triggers"),
            Map.entry("core_schedule_TRIGGERS", "core_schedule_triggers")
    );

    private final DataSource delegate;

    QuartzScheduleTableNameDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    static String normalizeSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String normalized = sql;
        for (Map.Entry<String, String> entry : TABLE_NAMES.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private static Connection wrapConnection(Connection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] rewrittenArgs = rewriteFirstSqlArgument(args);
            Object result = invoke(method, connection, rewrittenArgs);
            if (result instanceof Statement statement && "createStatement".equals(method.getName())) {
                return wrapStatement(statement);
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(
                QuartzScheduleTableNameDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static Statement wrapStatement(Statement statement) {
        InvocationHandler handler = (proxy, method, args) -> invoke(method, statement, rewriteFirstSqlArgument(args));
        return (Statement) Proxy.newProxyInstance(
                QuartzScheduleTableNameDataSource.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                handler
        );
    }

    private static Object[] rewriteFirstSqlArgument(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String sql)) {
            return args;
        }

        Object[] rewrittenArgs = args.clone();
        rewrittenArgs[0] = normalizeSql(sql);
        return rewrittenArgs;
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
