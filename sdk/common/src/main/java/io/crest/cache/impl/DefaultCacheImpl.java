package io.crest.cache.impl;

import io.crest.cache.CrestCacheService;
import io.crest.utils.LogUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.concurrent.TimeUnit;

/**
 * 基于 JCache 的默认缓存服务实现
 */
@ConditionalOnExpression("'${spring.cache.type}'.equals('jcache')")
@Component("crestCacheService")
public class DefaultCacheImpl implements CrestCacheService {


    private CacheManager cacheManager;

    @Resource
    private org.springframework.cache.CacheManager jcacheManager;


    /**
     * 写入指定缓存键，并按传入时间配置过期策略
     */
    @Override
    public void put(String cacheName, String key, Object value, Long expTime, TimeUnit unit) {
        Cache<String, Object> cache = null;
        if (ObjectUtils.isEmpty(cache = cacheManager.getCache(cacheName))) {
            try {
                cache = cacheManager.createCache(cacheName, configuration(expTime, unit));
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
                cache = cacheManager.getCache(cacheName);
            }

        }
        if (ObjectUtils.isNotEmpty(cache)) {
            cache.put(key, value);
        }

    }

    /**
     * 获取指定缓存键对应的值
     */
    @Override
    public Object get(String cacheName, String key) {
        Cache<Object, Object> cache = null;
        if (ObjectUtils.isEmpty(cache = cacheManager.getCache(cacheName))) {
            return null;
        }
        return cache.get(key);
    }

    /**
     * 判断指定缓存区域是否存在
     */
    @Override
    public boolean cacheExist(String cacheName) {
        return ObjectUtils.isNotEmpty(cacheManager.getCache(cacheName));
    }

    /**
     * 判断指定缓存键是否存在
     */
    @Override
    public boolean keyExist(String cacheName, String key) {
        Cache<Object, Object> cache = null;
        if (ObjectUtils.isEmpty(cache = cacheManager.getCache(cacheName))) {
            return false;
        }
        return cache.containsKey(key);
    }

    /**
     * 构建默认的一分钟过期缓存配置
     */
    private MutableConfiguration<String, Object> defaultConfiguration() {
        MutableConfiguration<String, Object> configuration =
                new MutableConfiguration<String, Object>()
                        .setTypes(String.class, Object.class)
                        .setStoreByValue(false)
                        .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
        return configuration;
    }

    /**
     * 根据过期时间构建缓存配置
     */
    private MutableConfiguration<String, Object> configuration(Long expTime, TimeUnit unit) {
        if (expTime <= 0) return defaultConfiguration();
        if (ObjectUtils.isEmpty(unit)) {
            unit = TimeUnit.SECONDS;
        }
        MutableConfiguration<String, Object> configuration =
                new MutableConfiguration<String, Object>()
                        .setTypes(String.class, Object.class)
                        .setStoreByValue(false)
                        .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(new Duration(unit, expTime)));
        return configuration;
    }

    /**
     * 移除指定缓存键
     */
    @Override
    public void keyRemove(String cacheName, String key) {
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        if(cache != null){
            cache.remove(key);
        }
    }

    /**
     * 初始化底层 JCache 缓存管理器
     */
    @PostConstruct
    public void init() {
        cacheManager = ((JCacheCacheManager) jcacheManager).getCacheManager();
    }

}
