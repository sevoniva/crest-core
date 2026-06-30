package io.crest.datasource.security;

import io.crest.datasource.dao.auto.entity.CoreDriver;
import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 校验 JDBC URL、驱动类型和危险参数的安全策略
 */
public final class JdbcUrlSecurityPolicy {

    private static final String DEFAULT_CUSTOM_DRIVER = "default";

    private static final Map<String, String> JDBC_PREFIXES = Map.ofEntries(
            Map.entry("mysql", "jdbc:mysql"),
            Map.entry("obmysql", "jdbc:oceanbase"),
            Map.entry("mongo", "jdbc:mysql"),
            Map.entry("mariadb", "jdbc:mysql"),
            Map.entry("starrocks", "jdbc:mysql"),
            Map.entry("doris", "jdbc:mysql"),
            Map.entry("tidb", "jdbc:mysql"),
            Map.entry("impala", "jdbc:impala"),
            Map.entry("sqlserver", "jdbc:sqlserver"),
            Map.entry("oracle", "jdbc:oracle"),
            Map.entry("oboracle", "jdbc:oceanbase"),
            Map.entry("db2", "jdbc:db2"),
            Map.entry("pg", "jdbc:postgresql"),
            Map.entry("redshift", "jdbc:redshift"),
            Map.entry("h2", "jdbc:h2"),
            Map.entry("ck", "jdbc:clickhouse")
    );

    private static final Map<String, String> DEFAULT_DRIVERS = Map.ofEntries(
            Map.entry("mysql", "com.mysql.cj.jdbc.Driver"),
            Map.entry("obmysql", "com.oceanbase.jdbc.Driver"),
            Map.entry("mongo", "com.mysql.cj.jdbc.Driver"),
            Map.entry("mariadb", "com.mysql.cj.jdbc.Driver"),
            Map.entry("starrocks", "com.mysql.cj.jdbc.Driver"),
            Map.entry("doris", "com.mysql.cj.jdbc.Driver"),
            Map.entry("tidb", "com.mysql.cj.jdbc.Driver"),
            Map.entry("impala", "com.cloudera.impala.jdbc.Driver"),
            Map.entry("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
            Map.entry("oracle", "oracle.jdbc.driver.OracleDriver"),
            Map.entry("oboracle", "com.oceanbase.jdbc.Driver"),
            Map.entry("db2", "com.ibm.db2.jcc.DB2Driver"),
            Map.entry("pg", "org.postgresql.Driver"),
            Map.entry("redshift", "com.amazon.redshift.jdbc42.Driver"),
            Map.entry("h2", "org.h2.Driver"),
            Map.entry("ck", "com.clickhouse.jdbc.ClickHouseDriver")
    );

    private static final Set<String> COMMON_DANGEROUS_FRAGMENTS = Set.of(
            "jndi:",
            "rmi:",
            "ldap:",
            "ldaps:",
            "dns:",
            "file:",
            "ftp:",
            "nis:",
            "corba:",
            "corbaname",
            "iiop",
            "iiopname",
            "java.naming.factory.initial",
            "java.naming.provider.url",
            "java.naming.factory.object",
            "java.naming.factory.state",
            "autodeserialize",
            "queryinterceptors",
            "statementinterceptors",
            "detectcustomcollations",
            "connectionproperties",
            "initsql"
    );

    private static final Map<String, Set<String>> TYPE_DANGEROUS_FRAGMENTS = Map.ofEntries(
            Map.entry("mysql", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("obmysql", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("mongo", Set.of()),
            Map.entry("mariadb", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("starrocks", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("doris", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("tidb", Set.of("maxallowedpacket", "allowloadlocalinfile", "allowurlinlocalinfile", "allowloadlocalinfileinpath", "allowmultiqueries")),
            Map.entry("impala", Set.of("krbjaasfile", "krb5.conf")),
            Map.entry("sqlserver", Set.of()),
            Map.entry("oracle", Set.of()),
            Map.entry("oboracle", Set.of()),
            Map.entry("db2", Set.of()),
            Map.entry("pg", Set.of("socketfactory", "socketfactoryarg", "sslfactory", "sslhostnameverifier", "sslpasswordcallback", "authenticationpluginclassname")),
            Map.entry("redshift", Set.of("socketfactory", "socketfactoryarg", "sslfactory", "sslhostnameverifier", "sslpasswordcallback", "authenticationpluginclassname", "inifile")),
            Map.entry("h2", Set.of("init=", "runscript")),
            Map.entry("ck", Set.of())
    );

    private JdbcUrlSecurityPolicy() {
    }

    /**
     * 校验数据源类型、连接地址和附加参数是否合法
     */
    public static String validate(String type, String jdbcUrl, String extraParams) {
        if (StringUtils.isBlank(jdbcUrl)) {
            CrestException.throwException("Illegal jdbcUrl: " + jdbcUrl);
        }
        String normalizedType = normalizeType(type);
        String normalizedUrl = canonicalize(jdbcUrl);
        String normalizedExtraParams = canonicalize(extraParams);
        String expectedPrefix = JDBC_PREFIXES.get(normalizedType);
        if (StringUtils.isBlank(expectedPrefix) || !hasJdbcSchemePrefix(normalizedUrl, expectedPrefix)) {
            CrestException.throwException("Illegal jdbcUrl: " + jdbcUrl);
        }
        Set<String> dangerousFragments = new LinkedHashSet<>(COMMON_DANGEROUS_FRAGMENTS);
        dangerousFragments.addAll(TYPE_DANGEROUS_FRAGMENTS.getOrDefault(normalizedType, Set.of()));
        for (String fragment : dangerousFragments) {
            if (normalizedUrl.contains(fragment) || normalizedExtraParams.contains(fragment)) {
                CrestException.throwException("Illegal parameter: " + fragment);
            }
        }
        return jdbcUrl;
    }

    /**
     * 获取指定数据源类型的可信默认驱动类
     */
    public static String trustedDriverClass(String type) {
        String driverClass = DEFAULT_DRIVERS.get(normalizeType(type));
        if (StringUtils.isBlank(driverClass)) {
            CrestException.throwException("invalid driver");
        }
        return driverClass;
    }

    /**
     * 根据默认驱动或已注册自定义驱动解析实际驱动类
     */
    public static String resolveDriverClass(String type, String requestedDriverClass, String customDriver, CoreDriver registeredDriver) {
        if (!isDefaultCustomDriver(customDriver)) {
            if (registeredDriver == null
                    || StringUtils.isBlank(registeredDriver.getDriverClass())
                    || !Strings.CI.equals(normalizeType(type), normalizeType(registeredDriver.getType()))) {
                CrestException.throwException("invalid driver");
            }
            return registeredDriver.getDriverClass();
        }
        String trustedDriverClass = trustedDriverClass(type);
        if (StringUtils.isNotBlank(requestedDriverClass) && !Strings.CI.equals(requestedDriverClass, trustedDriverClass)) {
            CrestException.throwException("invalid driver");
        }
        return trustedDriverClass;
    }

    /**
     * 判断自定义驱动配置是否表示默认驱动
     */
    public static boolean isDefaultCustomDriver(String customDriver) {
        return StringUtils.isBlank(customDriver) || Strings.CI.equals(customDriver, DEFAULT_CUSTOM_DRIVER);
    }

    /**
     * 对输入值执行解码、标准化和小写化处理
     */
    private static String canonicalize(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String normalized = value;
        for (int i = 0; i < 3; i++) {
            try {
                String decoded = URLDecoder.decode(normalized, StandardCharsets.UTF_8);
                if (Strings.CS.equals(decoded, normalized)) {
                    normalized = decoded;
                    break;
                }
                normalized = decoded;
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        normalized = normalized.replace("\\", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 校验完整 JDBC scheme 边界，避免 jdbc:mysql-evil 这类伪协议绕过 startsWith。
     */
    private static boolean hasJdbcSchemePrefix(String normalizedUrl, String expectedPrefix) {
        return normalizedUrl.startsWith(expectedPrefix + ":");
    }

    /**
     * 规范化数据源类型标识
     */
    private static String normalizeType(String type) {
        return StringUtils.defaultString(type).toLowerCase(Locale.ROOT);
    }
}
