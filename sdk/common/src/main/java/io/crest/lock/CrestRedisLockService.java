package io.crest.lock;

import io.crest.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class CrestRedisLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment environment;
    private final String ownerPrefix = UUID.randomUUID().toString();

    @Value("${crest.lock.key-prefix:crest:core:lock}")
    private String keyPrefix;

    @Value("${crest.lock.default-ttl-seconds:1800}")
    private long defaultTtlSeconds;

    public CrestRedisLockService(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Environment environment) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.environment = environment;
    }

    public Optional<LockHandle> tryLock(String lockName) {
        return tryLock(lockName, defaultTtl());
    }

    public Optional<LockHandle> tryLock(String lockName, Duration ttl) {
        if (!redisLockEnabled()) {
            return Optional.of(LockHandle.local());
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            if (productionMode()) {
                LogUtil.error("Redis lock skipped because StringRedisTemplate is not available: " + lockName);
                return Optional.empty();
            }
            return Optional.of(LockHandle.local());
        }

        String key = buildKey(lockName);
        String value = ownerPrefix + ":" + UUID.randomUUID();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, normalizeTtl(ttl));
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(new LockHandle(redisTemplate, key, value));
            }
            return Optional.empty();
        } catch (Exception e) {
            if (productionMode()) {
                LogUtil.error("Redis lock acquire failed: " + lockName, e);
                return Optional.empty();
            }
            LogUtil.warn("Redis lock unavailable, continue locally: " + lockName + ", " + e.getMessage());
            return Optional.of(LockHandle.local());
        }
    }

    public boolean runWithLock(String lockName, Runnable task) {
        Optional<LockHandle> lock = tryLock(lockName);
        if (lock.isEmpty()) {
            return false;
        }
        try (LockHandle ignored = lock.get()) {
            task.run();
            return true;
        }
    }

    private Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return defaultTtl();
        }
        return ttl;
    }

    private Duration defaultTtl() {
        return Duration.ofSeconds(defaultTtlSeconds > 0 ? defaultTtlSeconds : 1800);
    }

    private boolean redisLockEnabled() {
        return productionMode() || "redis".equalsIgnoreCase(environment.getProperty("spring.cache.type", ""));
    }

    private boolean productionMode() {
        return environment.getProperty("crest.production-mode", Boolean.class,
                environment.getProperty("CREST_PRODUCTION_MODE", Boolean.class, false));
    }

    private String buildKey(String lockName) {
        String normalizedName = StringUtils.defaultIfBlank(lockName, "default")
                .trim()
                .replaceAll("[^A-Za-z0-9:_\\-.]", "_");
        return StringUtils.defaultIfBlank(keyPrefix, "crest:core:lock") + ":" + normalizedName;
    }

    public static final class LockHandle implements AutoCloseable {
        private final StringRedisTemplate redisTemplate;
        private final String key;
        private final String value;

        private LockHandle(StringRedisTemplate redisTemplate, String key, String value) {
            this.redisTemplate = redisTemplate;
            this.key = key;
            this.value = value;
        }

        private static LockHandle local() {
            return new LockHandle(null, null, null);
        }

        @Override
        public void close() {
            if (redisTemplate == null) {
                return;
            }
            try {
                redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), value);
            } catch (Exception e) {
                LogUtil.warn("Redis lock release failed: " + key + ", " + e.getMessage());
            }
        }
    }
}
