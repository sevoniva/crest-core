package io.crest.runtime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CrestRuntimeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "crestRuntimeDefaults";
    private static final String[] REDIS_CLUSTER_SCOPED_KEYS = {
            "CREST_REDIS_CACHE_KEY_PREFIX",
            "spring.cache.redis.key-prefix",
            "CREST_LOCK_KEY_PREFIX",
            "crest.lock.key-prefix",
            "CREST_WEBSOCKET_BROADCAST_CHANNEL",
            "crest.websocket.broadcast.channel",
            "CREST_EXPORT_TASK_STREAM",
            "crest.task.queue.export.stream",
            "CREST_EXPORT_TASK_CONSUMER_GROUP",
            "crest.task.queue.export.consumer-group",
            "CREST_SYNC_TASK_STREAM",
            "crest.task.queue.sync.stream",
            "CREST_SYNC_TASK_CONSUMER_GROUP",
            "crest.task.queue.sync.consumer-group",
            "CREST_DATASOURCE_SYNC_TASK_STREAM",
            "crest.task.queue.datasource-sync.stream",
            "CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP",
            "crest.task.queue.datasource-sync.consumer-group",
            "CREST_SCHEDULED_TASK_STREAM",
            "crest.task.queue.scheduled.stream",
            "CREST_SCHEDULED_TASK_CONSUMER_GROUP",
            "crest.task.queue.scheduled.consumer-group"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        CrestRuntimeRole role = CrestRuntimeRole.from(environment);
        boolean productionMode = productionMode(environment);

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("CREST_RUNTIME_ROLE", role.getCode());
        defaults.put("crest.runtime.role", role.getCode());
        defaults.put("CREST_PRODUCTION_MODE", String.valueOf(productionMode));
        defaults.put("crest.production-mode", String.valueOf(productionMode));
        applyRedisDefaults(environment, defaults);
        applyQuartzDefaults(environment, defaults, productionMode);

        Map<String, Object> enforced = new LinkedHashMap<>();
        if (!role.runsScheduler()) {
            enforced.put("spring.quartz.auto-startup", "false");
        }

        if (productionMode) {
            enforced.put("spring.cache.type", "redis");
            enforced.put("management.health.redis.enabled", "true");
            enforced.put("CREST_FLYWAY_ENABLED", "false");
            enforced.put("spring.flyway.enabled", "false");
            enforced.put("spring.quartz.jdbc.initialize-schema", "never");
            enforced.put("spring.quartz.properties.org.quartz.jobStore.isClustered", "true");
            enforced.put("CREST_LOAD_DEMO", "false");
            enforced.put("crest.demo.enabled", "false");
        }

        if (!enforced.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME + "Enforced", enforced));
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        validateRuntimeRole(environment, role);
        validateProductionMode(environment);
        validateQuartzCluster(environment);
        validateRedisCluster(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }

    private boolean productionMode(ConfigurableEnvironment environment) {
        return environment.getProperty("crest.production-mode", Boolean.class,
                environment.getProperty("CREST_PRODUCTION_MODE", Boolean.class, false));
    }

    // 将 Crest 环境变量映射为 Spring Redis 配置，避免旧单节点部署被空值切换到 Cluster。
    private void applyRedisDefaults(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        putIfNotBlank(defaults, "spring.data.redis.username", environment.getProperty("CREST_REDIS_USERNAME"));
        putIfNotBlank(defaults, "spring.data.redis.ssl.enabled", environment.getProperty("CREST_REDIS_SSL_ENABLED"));
        putIfNotBlank(defaults, "spring.data.redis.connect-timeout", environment.getProperty("CREST_REDIS_CONNECT_TIMEOUT"));
        putIfNotBlank(defaults, "spring.data.redis.timeout", environment.getProperty("CREST_REDIS_TIMEOUT"));
        applyRedisNamespaceDefaults(environment, defaults);
        String clusterNodes = environment.getProperty("CREST_REDIS_CLUSTER_NODES");
        if (StringUtils.isNotBlank(clusterNodes)) {
            putIfNotBlank(defaults, "spring.data.redis.cluster.nodes", clusterNodes);
            putIfNotBlank(defaults, "spring.data.redis.cluster.max-redirects",
                    environment.getProperty("CREST_REDIS_CLUSTER_MAX_REDIRECTS"));
            putIfNotBlank(defaults, "spring.data.redis.lettuce.cluster.refresh.adaptive",
                    environment.getProperty("CREST_REDIS_CLUSTER_REFRESH_ADAPTIVE"));
            putIfNotBlank(defaults, "spring.data.redis.lettuce.cluster.refresh.period",
                    environment.getProperty("CREST_REDIS_CLUSTER_REFRESH_PERIOD"));
            putIfNotBlank(defaults, "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources",
                    environment.getProperty("CREST_REDIS_CLUSTER_DYNAMIC_REFRESH_SOURCES"));
        }
    }

    private void applyRedisNamespaceDefaults(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        String keyPrefix = environment.getProperty("CREST_REDIS_KEY_PREFIX");
        putIfNotBlank(defaults, "crest.redis.key-prefix", keyPrefix);
        String cacheKeyPrefix = environment.getProperty("CREST_REDIS_CACHE_KEY_PREFIX");
        if (StringUtils.isNotBlank(cacheKeyPrefix)) {
            defaults.putIfAbsent("spring.cache.redis.key-prefix", cacheKeyPrefix.trim());
        } else if (StringUtils.isNotBlank(keyPrefix)) {
            defaults.putIfAbsent("spring.cache.redis.key-prefix", trimTrailingColon(keyPrefix) + ":cache:");
        }
    }

    private void applyQuartzDefaults(ConfigurableEnvironment environment, Map<String, Object> defaults,
                                     boolean productionMode) {
        putIfNotBlank(defaults, "spring.quartz.properties.org.quartz.scheduler.instanceId",
                environment.getProperty("CREST_QUARTZ_INSTANCE_ID"));
        putIfNotBlank(defaults, "spring.quartz.properties.org.quartz.jobStore.isClustered",
                environment.getProperty("CREST_QUARTZ_CLUSTERED"));
        putIfNotBlank(defaults, "spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval",
                environment.getProperty("CREST_QUARTZ_CLUSTER_CHECKIN_INTERVAL"));
        putIfNotBlank(defaults, "spring.quartz.properties.org.quartz.jobStore.misfireThreshold",
                environment.getProperty("CREST_QUARTZ_MISFIRE_THRESHOLD"));
        if (productionMode) {
            defaults.putIfAbsent("spring.quartz.properties.org.quartz.scheduler.instanceId", "AUTO");
            defaults.putIfAbsent("spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval", "10000");
            defaults.putIfAbsent("spring.quartz.properties.org.quartz.jobStore.misfireThreshold", "60000");
        }
    }

    private void validateRuntimeRole(ConfigurableEnvironment environment, CrestRuntimeRole role) {
        if (role.runsScheduler()) {
            return;
        }
        requireEquals(environment, "spring.quartz.auto-startup", "false",
                "when CREST_RUNTIME_ROLE does not run the scheduler");
    }

    private void validateProductionMode(ConfigurableEnvironment environment) {
        if (!productionMode(environment)) {
            return;
        }
        String productionReason = "when CREST_PRODUCTION_MODE=true";
        requireEquals(environment, "spring.cache.type", "redis", productionReason);
        requireEquals(environment, "spring.flyway.enabled", "false", productionReason);
        requireEquals(environment, "spring.quartz.jdbc.initialize-schema", "never", productionReason);
        requireEquals(environment, "CREST_FLYWAY_ENABLED", "false", productionReason);
        requireEquals(environment, "crest.demo.enabled", "false", productionReason);
        requireEquals(environment, "CREST_LOAD_DEMO", "false", productionReason);
        requireEquals(environment, "management.health.redis.enabled", "true", productionReason);
        requireMinimumLength(environment, "crest.security.token-secret", "CREST_TOKEN_SECRET", 32, productionReason);
        requireExactLength(environment, "crest.crypto.aes-key", "CREST_AES_KEY", 32, productionReason);
        requireExactLength(environment, "crest.crypto.aes-iv", "CREST_AES_IV", 16, productionReason);
        requireMinimumLength(environment, "crest.user.initial-password", "CREST_INITIAL_PASSWORD", 12, productionReason);
    }

    private void validateQuartzCluster(ConfigurableEnvironment environment) {
        if (!productionMode(environment)) {
            return;
        }
        String productionReason = "when CREST_PRODUCTION_MODE=true";
        requireEquals(environment, "spring.quartz.properties.org.quartz.jobStore.isClustered", "true",
                productionReason);
        if (StringUtils.isBlank(environment.getProperty(
                "spring.quartz.properties.org.quartz.scheduler.instanceId"))) {
            throw new IllegalStateException("CREST_QUARTZ_INSTANCE_ID is required " + productionReason);
        }
    }

    // 生产模式必须使用带 hash tag 的 Redis Cluster 前缀，避免共享 Redis 中 key/channel/stream 命名冲突。
    private void validateRedisCluster(ConfigurableEnvironment environment) {
        boolean productionMode = productionMode(environment);
        String clusterNodes = environment.getProperty("spring.data.redis.cluster.nodes");
        if (StringUtils.isBlank(clusterNodes)) {
            if (productionMode) {
                throw new IllegalStateException("CREST_REDIS_CLUSTER_NODES is required when CREST_PRODUCTION_MODE=true");
            }
            return;
        }
        String database = StringUtils.defaultIfBlank(environment.getProperty("spring.data.redis.database"),
                environment.getProperty("CREST_REDIS_DATABASE", "0"));
        if (!"0".equals(database.trim())) {
            throw new IllegalStateException("CREST_REDIS_DATABASE must be 0 when CREST_REDIS_CLUSTER_NODES is configured");
        }
        String keyPrefix = StringUtils.defaultIfBlank(environment.getProperty("crest.redis.key-prefix"),
                environment.getProperty("CREST_REDIS_KEY_PREFIX"));
        String hashTag = redisHashTag(keyPrefix, "CREST_REDIS_KEY_PREFIX");
        if (productionMode) {
            validateRedisKeyPrefixNamespace(keyPrefix, "CREST_REDIS_KEY_PREFIX");
            validateRedisHashTagNamespace(hashTag, "CREST_REDIS_KEY_PREFIX");
            validateProductionRedisCluster(environment, clusterNodes);
        }
        validateRedisClusterScopedKeys(environment, hashTag);
    }

    private void validateProductionRedisCluster(ConfigurableEnvironment environment, String clusterNodes) {
        String[] nodes = StringUtils.split(clusterNodes, ',');
        if (nodes == null || nodes.length < 3) {
            throw new IllegalStateException("CREST_REDIS_CLUSTER_NODES must contain at least 3 nodes when CREST_PRODUCTION_MODE=true");
        }
        for (String node : nodes) {
            String trimmed = StringUtils.defaultString(node).trim();
            if (!validProductionRedisNode(trimmed) || redisNodeLooksLikeTemplate(trimmed)) {
                throw new IllegalStateException("CREST_REDIS_CLUSTER_NODES must contain real host:port entries when CREST_PRODUCTION_MODE=true");
            }
        }

        String username = StringUtils.defaultIfBlank(environment.getProperty("spring.data.redis.username"),
                environment.getProperty("CREST_REDIS_USERNAME"));
        if (StringUtils.isBlank(username)) {
            throw new IllegalStateException("CREST_REDIS_USERNAME is required for shared Redis ACL isolation when CREST_PRODUCTION_MODE=true");
        }
        if ("default".equalsIgnoreCase(username.trim())) {
            throw new IllegalStateException("CREST_REDIS_USERNAME must not be default for shared Redis when CREST_PRODUCTION_MODE=true");
        }

        String password = StringUtils.defaultIfBlank(environment.getProperty("spring.data.redis.password"),
                environment.getProperty("CREST_REDIS_PASSWORD"));
        String normalizedPassword = StringUtils.defaultString(password).trim();
        if (normalizedPassword.length() < 12) {
            throw new IllegalStateException("CREST_REDIS_PASSWORD must be at least 12 characters when CREST_PRODUCTION_MODE=true");
        }
        if ("password".equalsIgnoreCase(normalizedPassword) || "changeme".equalsIgnoreCase(normalizedPassword)) {
            throw new IllegalStateException("CREST_REDIS_PASSWORD must not use a weak common value when CREST_PRODUCTION_MODE=true");
        }
    }

    private void validateRedisClusterScopedKeys(ConfigurableEnvironment environment, String expectedHashTag) {
        for (String propertyName : REDIS_CLUSTER_SCOPED_KEYS) {
            String value = environment.getProperty(propertyName);
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String hashTag = redisHashTag(value, propertyName);
            if (!expectedHashTag.equals(hashTag)) {
                throw new IllegalStateException(propertyName + " must use the same Redis Cluster hash tag {"
                        + expectedHashTag + "} as CREST_REDIS_KEY_PREFIX");
            }
        }
    }

    private String redisHashTag(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(propertyName + " must use a Redis Cluster hash tag like {<org>-<env>-crest-core}:prod");
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.indexOf('}', start + 1);
        if (start < 0 || end < 0 || end == start + 1) {
            throw new IllegalStateException(propertyName + " must use a non-empty Redis Cluster hash tag like {<org>-<env>-crest-core}:prod");
        }
        return trimmed.substring(start + 1, end);
    }

    private void validateRedisHashTagNamespace(String hashTag, String propertyName) {
        String normalized = hashTag.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{7,63}")) {
            throw new IllegalStateException(propertyName
                    + " hash tag must be an 8-64 character lowercase namespace, for example {<org>-<env>-crest-core}:prod");
        }
        switch (normalized) {
            case "crest":
            case "crest-core":
            case "dataease":
            case "redis":
            case "cache":
            case "default":
            case "shared":
            case "system":
            case "app":
            case "application":
            case "prod":
            case "production":
                throw new IllegalStateException(propertyName
                        + " hash tag is too generic for shared Redis; use an environment-specific namespace");
            default:
                break;
        }
        if ("acme-crest-core-prod".equals(normalized) || normalized.contains("changeme")
                || normalized.contains("change-me") || normalized.contains("example") || normalized.contains("sample")
                || normalized.contains("demo") || normalized.contains("template")
                || normalized.contains("placeholder")) {
            throw new IllegalStateException(propertyName
                    + " hash tag looks like an example value; replace it with a real organization/environment namespace");
        }
    }

    private void validateRedisKeyPrefixNamespace(String keyPrefix, String propertyName) {
        String trimmed = StringUtils.defaultString(keyPrefix).trim();
        if (!trimmed.matches("\\{[a-z0-9][a-z0-9._-]{7,63}\\}:[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalStateException(propertyName
                    + " must look like {<org>-<env>-crest-core}:prod in shared Redis production mode");
        }
    }

    private boolean redisNodeLooksLikeTemplate(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("change_me") || normalized.contains("changeme")
                || normalized.contains("change-me") || normalized.contains("example")
                || normalized.contains("sample") || normalized.contains("demo")
                || normalized.contains("template") || normalized.contains("placeholder");
    }

    private boolean validProductionRedisNode(String value) {
        int portSeparator = value.lastIndexOf(':');
        if (portSeparator <= 0 || portSeparator == value.length() - 1) {
            return false;
        }
        String host = value.substring(0, portSeparator);
        String port = value.substring(portSeparator + 1);
        if (host.indexOf(':') >= 0 || host.matches(".*\\s+.*") || !port.matches("[0-9]{1,5}")) {
            return false;
        }
        int portNumber = Integer.parseInt(port);
        return portNumber >= 1 && portNumber <= 65535;
    }

    private String trimTrailingColon(String value) {
        String trimmed = StringUtils.defaultString(value).trim();
        while (trimmed.endsWith(":")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void requireEquals(ConfigurableEnvironment environment, String key, String expected, String reason) {
        String actual = environment.getProperty(key);
        if (!expected.equalsIgnoreCase(StringUtils.defaultString(actual).trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(key + " must be " + expected + " " + reason);
        }
    }

    private void requireMinimumLength(ConfigurableEnvironment environment, String propertyName, String envName,
                                      int minimumLength, String reason) {
        String value = StringUtils.defaultString(environment.getProperty(propertyName,
                environment.getProperty(envName))).trim();
        if (value.length() < minimumLength) {
            throw new IllegalStateException(envName + " must be at least " + minimumLength
                    + " characters " + reason);
        }
    }

    private void requireExactLength(ConfigurableEnvironment environment, String propertyName, String envName,
                                    int expectedLength, String reason) {
        String value = StringUtils.defaultString(environment.getProperty(propertyName,
                environment.getProperty(envName))).trim();
        if (value.length() != expectedLength) {
            throw new IllegalStateException(envName + " must be exactly " + expectedLength
                    + " characters " + reason);
        }
    }

    // 空值不写入 Spring 配置，保留框架默认行为。
    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }
}
