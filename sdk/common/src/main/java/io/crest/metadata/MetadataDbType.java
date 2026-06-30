package io.crest.metadata;

import com.baomidou.mybatisplus.annotation.DbType;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * Crest Core 首版只支持 OceanBase Oracle 作为元数据库。
 */
public enum MetadataDbType {
    OB_ORACLE("ob-oracle", "com.oceanbase.jdbc.Driver", DbType.ORACLE, "ob-oracle",
            "classpath:db/migration-ob-oracle");

    private final String code;
    private final String driverClassName;
    private final DbType mybatisPlusDbType;
    private final String databaseId;
    private final String defaultFlywayLocations;

    MetadataDbType(String code, String driverClassName, DbType mybatisPlusDbType,
                   String databaseId, String defaultFlywayLocations) {
        this.code = code;
        this.driverClassName = driverClassName;
        this.mybatisPlusDbType = mybatisPlusDbType;
        this.databaseId = databaseId;
        this.defaultFlywayLocations = defaultFlywayLocations;
    }

    // 对外暴露的配置编码，直接对应 CREST_DB_TYPE
    public String getCode() {
        return code;
    }

    // Spring 数据源默认驱动类，由环境后处理器写入占位配置
    public String getDriverClassName() {
        return driverClassName;
    }

    // MyBatis Plus 分页和 SQL 片段所需的数据库枚举
    public DbType getMybatisPlusDbType() {
        return mybatisPlusDbType;
    }

    // MyBatis databaseId，用于按元数据库类型选择 mapper 语句
    public String getDatabaseId() {
        return databaseId;
    }

    // 默认 Flyway 目录，安装脚本和本地启动共用同一份约定
    public String getDefaultFlywayLocations() {
        return defaultFlywayLocations;
    }

    // 首版不加载演示迁移，demoEnabled 参数保留给调用方接口稳定性。
    public String getDefaultFlywayLocations(boolean demoEnabled) {
        return defaultFlywayLocations;
    }

    // OceanBase 元库按外部企业数据库处理，启动默认不主动迁移
    public boolean isOceanBase() {
        return true;
    }

    // 生产初始化由 DBA 执行离线 SQL，应用启动不主动修改元数据库
    public boolean isFlywayEnabledByDefault() {
        return false;
    }

    // Oracle 兼容路径需要额外启用标识符引用和主键生成适配
    public boolean isOracleCompatible() {
        return this == OB_ORACLE;
    }

    // 解析配置值时兼容下划线写法，并对未知类型尽早失败
    public static MetadataDbType from(String value) {
        String normalized = value == null ? OB_ORACLE.code : value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            return OB_ORACLE;
        }
        for (MetadataDbType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Crest Core only supports CREST_DB_TYPE=ob-oracle");
    }
}
