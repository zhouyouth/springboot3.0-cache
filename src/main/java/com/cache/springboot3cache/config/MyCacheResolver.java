package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyCacheResolver implements CacheResolver {

    private static final Logger logger = LoggerFactory.getLogger(MyCacheResolver.class);
    private static final Pattern PATTERN_WITH_REFRESH = Pattern.compile("(.+)#(\\d+)#(\\d+)");
    private static final Pattern PATTERN_ONLY_EXPIRE = Pattern.compile("(.+)#(\\d+)");
    
    // 默认分布式锁超时时间（秒）
    private static final long DEFAULT_LOCK_TIMEOUT_SECONDS = 60;

    private final CacheManager cacheManager;
    private final RedisCacheWriter cacheWriter;
    private final RedisCacheConfiguration defaultCacheConfig;
    private final Executor cacheRefreshExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate;

    public MyCacheResolver(CacheManager cacheManager, RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfig, Executor cacheRefreshExecutor, StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        this.cacheManager = cacheManager;
        this.cacheWriter = cacheWriter;
        this.defaultCacheConfig = defaultCacheConfig;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        logger.info("MyCacheResolver initialized");
    }

    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        String cacheName = context.getOperation().getCacheNames().iterator().next();
        logger.info("Resolving cache for name: {}", cacheName);
        
        // 1. 匹配 name#expire#refresh
        Matcher matcherRefresh = PATTERN_WITH_REFRESH.matcher(cacheName);
        if (matcherRefresh.find()) {
            String name = matcherRefresh.group(1);
            long expire = Long.parseLong(matcherRefresh.group(2));
            long refreshCountdown = Long.parseLong(matcherRefresh.group(3));

            if (refreshCountdown >= expire) {
                long correctedCountdown = Math.max(1, expire / 2);
                logger.warn("Invalid cache configuration for '{}': refreshCountdown ({}) >= expire ({}). Correcting to {}s.",
                        cacheName, refreshCountdown, expire, correctedCountdown);
                refreshCountdown = correctedCountdown;
            }

            long refreshAge = expire - refreshCountdown;
            long physicalTtl = expire + refreshCountdown;

            RedisCacheConfiguration config = defaultCacheConfig.entryTtl(Duration.ofSeconds(physicalTtl));
            
            // 创建 CustomRedisCache，传入 context 和 redisTemplate，以及锁超时时间
            CustomRedisCache customCache = new CustomRedisCache(name, cacheWriter, config, refreshAge, DEFAULT_LOCK_TIMEOUT_SECONDS, cacheRefreshExecutor, stringRedisTemplate, redisTemplate, context);
            logger.info("Created CustomRedisCache: {}", System.identityHashCode(customCache));
            return Collections.singletonList(customCache);
        }

        // 2. 匹配 name#expire
        Matcher matcherExpire = PATTERN_ONLY_EXPIRE.matcher(cacheName);
        if (matcherExpire.find()) {
            String name = matcherExpire.group(1);
            long expire = Long.parseLong(matcherExpire.group(2));
            
            RedisCacheConfiguration config = defaultCacheConfig.entryTtl(Duration.ofSeconds(expire));
            
            // 使用 CustomRedisCache，refreshAge = -1 (不触发刷新)
            // 传入 redisTemplate，context 为 null
            return Collections.singletonList(new CustomRedisCache(name, cacheWriter, config, -1, DEFAULT_LOCK_TIMEOUT_SECONDS, cacheRefreshExecutor, stringRedisTemplate, redisTemplate, null));
        }

        // 3. 默认情况
        return Collections.singletonList(cacheManager.getCache(cacheName));
    }
}
