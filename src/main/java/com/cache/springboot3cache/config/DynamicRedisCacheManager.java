package com.cache.springboot3cache.config;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态Redis缓存管理器
 * 支持解析缓存名称中的过期时间和刷新时间配置
 * 格式：cacheName#expireTime#refreshTime
 */
public class DynamicRedisCacheManager extends RedisCacheManager {

    // 缓存名称解析正则表达式：name#expire#refresh
    private static final Pattern PATTERN = Pattern.compile("(.+)#(\\d+)#(\\d+)");
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
        Matcher matcher = PATTERN.matcher(name);
        if (matcher.find()) {
            String cacheName = matcher.group(1);
            long expire = Long.parseLong(matcher.group(2));
            long refresh = Long.parseLong(matcher.group(3));

            // 设置过期时间
            RedisCacheConfiguration config = cacheConfig.entryTtl(Duration.ofSeconds(expire));
            
            // 创建支持自动刷新的CustomRedisCache
            return new CustomRedisCache(cacheName, cacheWriter, config, refresh, cacheRefreshExecutor);
        }
        return super.createRedisCache(name, cacheConfig);
    }
}
