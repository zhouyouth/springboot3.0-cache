package com.cache.springboot3cache.config;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicRedisCacheManager extends RedisCacheManager {

    private static final Pattern PATTERN = Pattern.compile("(.+)#(\\d+)#(\\d+)");
    private final RedisCacheWriter cacheWriter;
    private final Executor cacheRefreshExecutor;

    public DynamicRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, Executor cacheRefreshExecutor) {
        super(cacheWriter, defaultCacheConfiguration);
        this.cacheWriter = cacheWriter;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        Matcher matcher = PATTERN.matcher(name);
        if (matcher.find()) {
            String cacheName = matcher.group(1);
            long expire = Long.parseLong(matcher.group(2));
            long refresh = Long.parseLong(matcher.group(3));

            RedisCacheConfiguration config = cacheConfig.entryTtl(Duration.ofSeconds(expire));
            
            return new CustomRedisCache(cacheName, cacheWriter, config, refresh, cacheRefreshExecutor);
        }
        return super.createRedisCache(name, cacheConfig);
    }
}
