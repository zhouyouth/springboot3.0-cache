package com.cache.springboot3cache.config;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * 动态Redis缓存管理器
 * 支持解析缓存名称中的过期时间和刷新时间配置
 * 格式：cacheName#expireTime#gracePeriod
 */
public class DynamicRedisCacheManager extends RedisCacheManager {

    private final RedisCacheWriter cacheWriter;
    private final Executor cacheRefreshExecutor;

    /**
     * 构造函数
     *
     * @param cacheWriter Redis缓存写入器
     * @param defaultCacheConfiguration 默认缓存配置
     * @param cacheRefreshExecutor 异步刷新线程池
     */
    public DynamicRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, Executor cacheRefreshExecutor) {
        super(cacheWriter, defaultCacheConfiguration);
        this.cacheWriter = cacheWriter;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
    }

    /**
     * 创建Redis缓存实例
     * 根据缓存名称解析过期时间和刷新时间，如果匹配格式则创建CustomRedisCache
     * 否则创建默认的RedisCache
     *
     * @param name 缓存名称
     * @param cacheConfig 缓存配置
     * @return RedisCache 缓存实例
     */
    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        // 使用 split 替代正则，支持更灵活的配置格式
        String[] parts = name.split("#");
        String cacheName = parts[0];

        if (parts.length > 1) {
            long expire = Long.parseLong(parts[1]);
            // 默认不设置宽限期/刷新逻辑
            RedisCacheConfiguration config = cacheConfig.entryTtl(Duration.ofSeconds(expire));

            // 如果配置了第三个参数 (gracePeriod)，则启用 CustomRedisCache 的刷新逻辑
            if (parts.length > 2) {
                long gracePeriod = Long.parseLong(parts[2]);

                // 刷新触发点：逻辑过期时间 - 宽限期
                // 例如：6#3 -> 6 - 3 = 3秒后开始刷新
                long refreshAge = expire - gracePeriod;
                if (refreshAge < 0) {
                    refreshAge = 0; // 避免负数
                }

                // 物理TTL = 逻辑过期时间 + 宽限期
                // 这为异步刷新提供了足够的时间，防止物理过期
                long physicalTtl = expire + gracePeriod;
                config = config.entryTtl(Duration.ofSeconds(physicalTtl));

                // 创建支持自动刷新的CustomRedisCache
                return new CustomRedisCache(cacheName, cacheWriter, config, refreshAge, cacheRefreshExecutor);
            }

            // 仅设置了过期时间，使用默认 RedisCache (或根据需求决定是否用 CustomRedisCache 但不刷新)
            return super.createRedisCache(cacheName, config);
        }
        return super.createRedisCache(name, cacheConfig);
    }
}
