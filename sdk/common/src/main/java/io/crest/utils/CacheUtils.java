package io.crest.utils;


import io.crest.cache.CrestCacheService;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@SuppressWarnings("unchecked")
/**
 * 缓存访问工具，封装常用读写和延迟删除操作
 */
public class CacheUtils {

    private static CrestCacheService cacheService;

    static {
        getService();
    }

    /**
     * 获取缓存服务实例
     */
    private static CrestCacheService getService() {
        if (ObjectUtils.isEmpty(cacheService)) {
            cacheService = (CrestCacheService) CommonBeanFactory.getBean("crestCacheService");
        }
        return cacheService;
    }

    /**
     * 写入默认 8 小时过期的缓存
     */
    public static void put(String cacheName, String key, Object val) {
        cacheService.put(cacheName, key, val, 8L, TimeUnit.HOURS);
    }

    /**
     * 写入指定过期时间的缓存
     */
    public static void put(String cacheName, String key, Object val, Long expTime, TimeUnit unit) {
        cacheService.put(cacheName, key, val, expTime, unit);
    }

    /**
     * 读取指定缓存值
     */
    public static Object get(String cacheName, String key) {
        return cacheService.get(cacheName, key);
    }

    /**
     * 判断指定缓存键是否存在
     */
    public static Boolean keyExist(String cacheName, String key) {
        return cacheService.keyExist(cacheName, key);
    }

    /**
     * 删除指定缓存键
     */
    public static void keyRemove(String cacheName, String key) {
        cacheService.keyRemove(cacheName, key);
    }

    /**
     * 删除单个缓存并在回调后延迟再删一次
     */
    public static void remove(String cacheName, String key, Consumer<Object> consumer) {
        cacheService.keyRemove(cacheName, key);
        consumer.accept(null);
        DelayQueueUtils.execute(IDUtils.randomID(16), () -> {
            cacheService.keyRemove(cacheName, key);
        }, 1L);
    }

    /**
     * 删除多个缓存空间中的同一个键，并在回调后延迟再删一次
     */
    public static void remove(String[] cacheNames, String key, Consumer<Object> consumer) {
        Arrays.stream(cacheNames).forEach(cacheName -> cacheService.keyRemove(cacheName, key));
        consumer.accept(null);
        DelayQueueUtils.execute(IDUtils.randomID(16), () -> {
            Arrays.stream(cacheNames).forEach(cacheName -> cacheService.keyRemove(cacheName, key));
        }, 1L);

    }

    /**
     * 删除单个缓存空间中的多个键，并在回调后延迟再删一次
     */
    public static void remove(String cacheName, List<String> keys, Consumer<Object> consumer) {
        keys.forEach(key -> cacheService.keyRemove(cacheName, key));
        consumer.accept(null);
        DelayQueueUtils.execute(IDUtils.randomID(16), () -> {
            keys.forEach(key -> cacheService.keyRemove(cacheName, key));
        }, 1L);
    }

    /**
     * 删除多个缓存空间中的多个键，并在回调后延迟再删一次
     */
    public static void remove(String[] cacheNames, List<String> keys, Consumer<Object> consumer) {
        Arrays.stream(cacheNames).forEach(cacheName -> {
            keys.forEach(key -> cacheService.keyRemove(cacheName, key));
        });
        consumer.accept(null);
        DelayQueueUtils.execute(IDUtils.randomID(16), () -> {
            Arrays.stream(cacheNames).forEach(cacheName -> {
                keys.forEach(key -> cacheService.keyRemove(cacheName, key));
            });
        }, 1L);
    }
}
