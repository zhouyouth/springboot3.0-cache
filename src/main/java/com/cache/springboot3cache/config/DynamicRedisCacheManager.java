package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 格式1：cacheName#expireTime#refreshTime (支持自动刷新)
 * 格式2：cacheName#expireTime (仅物理过期，不刷新)
 */
public class DynamicRedisCacheManager extends RedisCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(DynamicRedisCacheManager.class);
    
    // 格式1：name#expire#refresh
    private static final Pattern PATTERN_WITH_REFRESH = Pattern.compile("(.+)#(\\d+)#(\\d+)");
    
    // 格式2：name#expire
    private static final Pattern PATTERN_ONLY_EXPIRE = Pattern.compile("(.+)#(\\d+)");
    
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
     * 根据缓存名称解析配置
     *
     * @param name 缓存名称
     * @param cacheConfig 缓存配置
     * @return RedisCache 缓存实例
     */
    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        // 1. 尝试匹配 name#expire#refresh 格式
        Matcher matcherRefresh = PATTERN_WITH_REFRESH.matcher(name);
        if (matcherRefresh.find()) {
            String cacheName = matcherRefresh.group(1);
            long expire = Long.parseLong(matcherRefresh.group(2));
            long refreshCountdown = Long.parseLong(matcherRefresh.group(3));

            // 约束校验：刷新倒计时必须小于逻辑过期时间，且至少为1秒
            if (refreshCountdown >= expire) {
                long correctedCountdown = Math.max(1, expire / 2);
                logger.warn("Invalid cache configuration for '{}': refreshCountdown ({}) >= expire ({}). " +
                        "Correcting refreshCountdown to {}s.",
                        name, refreshCountdown, expire, correctedCountdown);
                refreshCountdown = correctedCountdown;
            }
            if (refreshCountdown < 1) {
                refreshCountdown = 1; // 刷新倒计时至少为1秒
            }

            long refreshAge = expire - refreshCountdown;
            long physicalTtl = expire + refreshCountdown;

            RedisCacheConfiguration config = cacheConfig.entryTtl(Duration.ofSeconds(physicalTtl));
            return new CustomRedisCache(cacheName, cacheWriter, config, refreshAge, cacheRefreshExecutor);
        }

        // 2. 尝试匹配 name#expire 格式
        Matcher matcherExpire = PATTERN_ONLY_EXPIRE.matcher(name);
        if (matcherExpire.find()) {
            String cacheName = matcherExpire.group(1);
            long expire = Long.parseLong(matcherExpire.group(2));

            // 仅设置物理过期时间，使用默认的RedisCache实现（无自动刷新逻辑）
            RedisCacheConfiguration config = cacheConfig.entryTtl(Duration.ofSeconds(expire));
            return super.createRedisCache(cacheName, config);
        }

        // 3. 默认情况
        return super.createRedisCache(name, cacheConfig);
    }
}
