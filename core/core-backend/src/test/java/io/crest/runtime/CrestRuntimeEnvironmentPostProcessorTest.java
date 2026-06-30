package io.crest.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrestRuntimeEnvironmentPostProcessorTest {

    private static final String PROD_TOKEN_SECRET = "token-secret-value-12345678901234567890";
    private static final String PROD_AES_KEY = "12345678901234567890123456789012";
    private static final String PROD_AES_IV = "1234567890123456";
    private static final String PROD_INITIAL_PASSWORD = "admin-secret-123";

    private final CrestRuntimeEnvironmentPostProcessor processor = new CrestRuntimeEnvironmentPostProcessor();

    @Test
    void defaultsKeepAllInOneDeploymentCompatible() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("CREST_RUNTIME_ROLE")).isEqualTo("all");
        assertThat(environment.getProperty("crest.runtime.role")).isEqualTo("all");
        assertThat(environment.getProperty("crest.production-mode")).isEqualTo("false");
        assertThat(environment.getProperty("spring.cache.type")).isNull();
    }

    @Test
    void apiRoleDisablesQuartzAutoStartupByDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_RUNTIME_ROLE", "api");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.quartz.auto-startup")).isEqualTo("false");
    }

    @Test
    void apiRoleForcesQuartzAutoStartupDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_RUNTIME_ROLE", "api")
                .withProperty("spring.quartz.auto-startup", "true");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.quartz.auto-startup")).isEqualTo("false");
    }

    @Test
    void workerRoleDisablesQuartzAutoStartupByDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_RUNTIME_ROLE", "worker");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.quartz.auto-startup")).isEqualTo("false");
    }

    @Test
    void schedulerRoleKeepsQuartzAutoStartupUnsetByDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_RUNTIME_ROLE", "scheduler");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.quartz.auto-startup")).isNull();
    }

    @Test
    void productionModeAddsSafeDefaults() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value")
                .withProperty("spring.cache.type", "jcache");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cache.type")).isEqualTo("redis");
        assertThat(environment.getProperty("management.health.redis.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("CREST_FLYWAY_ENABLED")).isEqualTo("false");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(environment.getProperty("spring.quartz.properties.org.quartz.jobStore.isClustered"))
                .isEqualTo("true");
        assertThat(environment.getProperty("spring.quartz.properties.org.quartz.scheduler.instanceId"))
                .isEqualTo("AUTO");
        assertThat(environment.getProperty("CREST_LOAD_DEMO")).isEqualTo("false");
        assertThat(environment.getProperty("crest.demo.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.data.redis.username")).isEqualTo("crest-prod");
        assertThat(environment.getProperty("crest.redis.key-prefix")).isEqualTo("{ops01-prod-crest-core}:prod");
        assertThat(environment.getProperty("spring.cache.redis.key-prefix"))
                .isEqualTo("{ops01-prod-crest-core}:prod:cache:");
    }

    @Test
    void productionModeForcesQuartzClustered() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value")
                .withProperty("CREST_QUARTZ_CLUSTERED", "false");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.quartz.properties.org.quartz.jobStore.isClustered"))
                .isEqualTo("true");
    }

    @Test
    void productionModeForcesFlywayDisabled() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value")
                .withProperty("CREST_FLYWAY_ENABLED", "true");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("CREST_FLYWAY_ENABLED")).isEqualTo("false");
    }

    @Test
    void productionModeRequiresTokenSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_PRODUCTION_MODE", "true");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_TOKEN_SECRET");
    }

    @Test
    void productionModeRequiresStrongRuntimeSecrets() {
        assertThatThrownBy(() -> processor.postProcessEnvironment(
                productionEnvironment().withProperty("CREST_TOKEN_SECRET", "short"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_TOKEN_SECRET")
                .hasMessageContaining("at least 32 characters");

        assertThatThrownBy(() -> processor.postProcessEnvironment(
                productionEnvironment().withProperty("CREST_AES_KEY", "too-short"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_AES_KEY")
                .hasMessageContaining("exactly 32 characters");

        assertThatThrownBy(() -> processor.postProcessEnvironment(
                productionEnvironment().withProperty("CREST_AES_IV", "too-short"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_AES_IV")
                .hasMessageContaining("exactly 16 characters");

        assertThatThrownBy(() -> processor.postProcessEnvironment(
                productionEnvironment().withProperty("CREST_INITIAL_PASSWORD", "short"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_INITIAL_PASSWORD")
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void redisClusterNodesCanBeConfiguredWithCrestEnvironmentKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:test")
                .withProperty("CREST_REDIS_CLUSTER_MAX_REDIRECTS", "5")
                .withProperty("CREST_REDIS_USERNAME", "crest")
                .withProperty("CREST_REDIS_SSL_ENABLED", "true")
                .withProperty("CREST_REDIS_CONNECT_TIMEOUT", "2s")
                .withProperty("CREST_REDIS_TIMEOUT", "5s")
                .withProperty("CREST_REDIS_CLUSTER_REFRESH_ADAPTIVE", "true")
                .withProperty("CREST_REDIS_CLUSTER_REFRESH_PERIOD", "30s")
                .withProperty("CREST_REDIS_CLUSTER_DYNAMIC_REFRESH_SOURCES", "true");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.cluster.nodes")).isEqualTo("redis-0:6379,redis-1:6379");
        assertThat(environment.getProperty("spring.data.redis.cluster.max-redirects")).isEqualTo("5");
        assertThat(environment.getProperty("spring.data.redis.username")).isEqualTo("crest");
        assertThat(environment.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.data.redis.connect-timeout")).isEqualTo("2s");
        assertThat(environment.getProperty("spring.data.redis.timeout")).isEqualTo("5s");
        assertThat(environment.getProperty("spring.data.redis.lettuce.cluster.refresh.adaptive")).isEqualTo("true");
        assertThat(environment.getProperty("spring.data.redis.lettuce.cluster.refresh.period")).isEqualTo("30s");
        assertThat(environment.getProperty("spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources")).isEqualTo("true");
    }

    @Test
    void redisClusterUsesExplicitCacheKeyPrefixForSpringCache() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:test")
                .withProperty("CREST_REDIS_CACHE_KEY_PREFIX", "{crest-core}:test:cache:v2:");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cache.redis.key-prefix"))
                .isEqualTo("{crest-core}:test:cache:v2:");
    }

    @Test
    void explicitSpringRedisClusterNodesKeepPriority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "crest-env:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:test")
                .withProperty("spring.data.redis.cluster.nodes", "spring-env:6379");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.cluster.nodes")).isEqualTo("spring-env:6379");
    }

    @Test
    void redisClusterRedirectsDoNotEnableClusterWithoutNodes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_MAX_REDIRECTS", "3");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.cluster.nodes")).isNull();
        assertThat(environment.getProperty("spring.data.redis.cluster.max-redirects")).isNull();
    }

    @Test
    void redisClusterRejectsNonZeroDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:test")
                .withProperty("CREST_REDIS_DATABASE", "1");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_DATABASE");
    }

    @Test
    void productionModeRequiresRedisClusterNodes() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_CLUSTER_NODES");
    }

    @Test
    void productionModeRequiresAtLeastThreeRedisClusterNodes() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 3 nodes");
    }

    @Test
    void productionModeRejectsPlaceholderRedisClusterNodes() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "CHANGE_ME_REDIS_NODE_1:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real host:port");
    }

    @Test
    void productionModeRejectsInvalidRedisClusterNodePort() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:70000,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real host:port");
    }

    @Test
    void productionModeRequiresRedisAclUsername() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_USERNAME");
    }

    @Test
    void productionModeRejectsDefaultRedisAclUsername() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "default")
                .withProperty("CREST_REDIS_PASSWORD", "redis-secret-value");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be default");
    }

    @Test
    void productionModeRequiresRedisPassword() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{ops01-prod-crest-core}:prod")
                .withProperty("CREST_REDIS_USERNAME", "crest-prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_PASSWORD");
    }

    @Test
    void redisClusterRejectsPrefixWithoutHashTag() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "ops01-prod-crest-core");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_KEY_PREFIX");
    }

    @Test
    void redisClusterRejectsEmptyHashTagPrefix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{}:prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty Redis Cluster hash tag");
    }

    @Test
    void redisClusterRejectsScopedKeysWithDifferentHashTag() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:prod")
                .withProperty("CREST_EXPORT_TASK_STREAM", "{other-system}:prod:stream:export-task");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_EXPORT_TASK_STREAM")
                .hasMessageContaining("{crest-core}");
    }

    @Test
    void redisClusterRejectsCachePrefixWithDifferentHashTag() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:prod")
                .withProperty("CREST_REDIS_CACHE_KEY_PREFIX", "{other-system}:prod:cache:");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREST_REDIS_CACHE_KEY_PREFIX")
                .hasMessageContaining("{crest-core}");
    }

    @Test
    void productionModeRejectsGenericRedisHashTagNamespace() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{crest-core}:prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too generic");
    }

    @Test
    void productionModeRejectsExampleRedisHashTagNamespace() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "{acme-crest-core-prod}:prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("example value");
    }

    @Test
    void productionModeRejectsRedisPrefixWithHashTagInTheMiddle() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("CREST_REDIS_CLUSTER_NODES", "redis-0:6379,redis-1:6379,redis-2:6379")
                .withProperty("CREST_REDIS_KEY_PREFIX", "crest:{ops01-prod-crest-core}:prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must look like");
    }

    private MockEnvironment productionEnvironment() {
        return new MockEnvironment()
                .withProperty("CREST_PRODUCTION_MODE", "true")
                .withProperty("CREST_TOKEN_SECRET", PROD_TOKEN_SECRET)
                .withProperty("CREST_AES_KEY", PROD_AES_KEY)
                .withProperty("CREST_AES_IV", PROD_AES_IV)
                .withProperty("CREST_INITIAL_PASSWORD", PROD_INITIAL_PASSWORD);
    }
}
