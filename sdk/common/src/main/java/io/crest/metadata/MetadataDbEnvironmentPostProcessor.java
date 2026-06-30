package io.crest.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在识别 CREST_DB_TYPE 后注入元数据库默认配置
 */
public class MetadataDbEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "crestMetadataDbDefaults";

    // 将元数据库类型推导出的默认值放在最后，保留用户显式配置的优先级
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MetadataDbType type = MetadataDbTypeResolver.resolve(environment);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("CREST_DB_TYPE", type.getCode());
        defaults.put("CREST_DB_DRIVER_CLASS_NAME", type.getDriverClassName());
        defaults.put("CREST_DB_URL", oceanBaseJdbcUrl());
        boolean demoEnabled = environment.getProperty("CREST_LOAD_DEMO", Boolean.class,
                environment.getProperty("crest.demo.enabled", Boolean.class, true));
        defaults.put("CREST_FLYWAY_ENABLED", String.valueOf(type.isFlywayEnabledByDefault()));
        defaults.put("CREST_FLYWAY_LOCATIONS", type.getDefaultFlywayLocations(demoEnabled));
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    // 低优先级执行，确保前置配置源已经完成加载
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // OceanBase Oracle 租户不带库名路径，schema 由登录用户决定
    private String oceanBaseJdbcUrl() {
        return "jdbc:oceanbase://${CREST_DB_HOST:127.0.0.1}:${CREST_DB_PORT:2883}";
    }
}
