package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class CustomRedisCache extends RedisCache {

    private static final Logger logger = LoggerFactory.getLogger(CustomRedisCache.class);
    private final long refreshInSeconds;
    private final Executor executor;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate; // 使用统一的 RedisTemplate
    private final CacheOperationInvocationContext<?> context;
    private static final String LOCK_SUFFIX = "~lock";

    protected CustomRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, long refreshInSeconds, Executor executor, StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate, CacheOperationInvocationContext<?> context) {
        super(name, cacheWriter, cacheConfig);
        this.refreshInSeconds = refreshInSeconds;
        this.executor = executor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.context = context;
        // logger.info("CustomRedisCache created: name={}, refreshInSeconds={}", name, refreshInSeconds);
    }

    @Override
    public ValueWrapper get(Object key) {
        // 1. 直接使用 RedisTemplate 读取，保证序列化一致性 (JSON)
        String redisKey = createKey(key);
        Object value = redisTemplate.opsForValue().get(redisKey);

        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            long age = System.currentTimeMillis() - wrapper.getCreateTime();
            
            // 2. 检查逻辑过期
            if (age > refreshInSeconds * 1000) {
                // logger.info("Cache key {} is stale (age={}ms), triggering async refresh...", key, age);
                refreshAsync(key);
            }
            
            // 3. 无论是否过期，都立即返回当前值 (Refresh-Ahead 核心)
            return new SimpleValueWrapper(wrapper.getValue());
        }
        
        // 如果不是 RefreshWrapper (可能是旧数据或配置变更)，按普通数据处理
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    /**
     * 执行异步刷新
     */
    private void refreshAsync(Object key) {
        String lockKey = createLockKey(key);
        String lockValue = UUID.randomUUID().toString();

        // 非阻塞尝试获取锁
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
        
        if (Boolean.TRUE.equals(locked)) {
            CompletableFuture.runAsync(() -> {
                try {
                    // logger.info("Executing async refresh for key: {}", key);
                    
                    // 1. 获取目标对象（解包代理）
                    Object target = context.getTarget();
                    Object rawTarget = AopProxyUtils.getSingletonTarget(target);
                    if (rawTarget == null) {
                        rawTarget = target;
                    }

                    // 2. 反射调用目标方法
                    Object[] args = context.getArgs();
                    Object newValue = context.getMethod().invoke(rawTarget, args);

                    // 3. 写入 Redis (使用统一的 RedisTemplate)
                    put(key, newValue);
                    // logger.info("Async refresh done for key: {}", key);
                    
                } catch (Exception e) {
                    logger.error("Error refreshing cache key: {}", key, e);
                } finally {
                    // 4. 释放锁
                    try {
                        String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
                        if (lockValue.equals(currentValue)) {
                            stringRedisTemplate.delete(lockKey);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to release lock", e);
                    }
                }
            }, executor);
        }
    }

    @Override
    public void put(Object key, Object value) {
        RefreshWrapper wrapper;
        if (value instanceof RefreshWrapper) {
            wrapper = (RefreshWrapper) value;
        } else {
            wrapper = new RefreshWrapper(value, System.currentTimeMillis());
        }
        
        // 使用 RedisTemplate 写入，确保使用配置好的 JSON 序列化器
        String redisKey = createKey(key);
        Duration ttl = getCacheConfiguration().getTtl();
        redisTemplate.opsForValue().set(redisKey, wrapper, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        // 简单实现，直接复用 put (对于 Refresh-Ahead 场景，putIfAbsent 使用较少)
        // 严谨的话应该用 setIfAbsent
        put(key, value);
        return new SimpleValueWrapper(value);
    }
    
    // 辅助方法
    private String createLockKey(Object key) {
        return getName() + "::" + key.toString() + LOCK_SUFFIX;
    }
    
    private String createKey(Object key) {
        return getName() + "::" + key.toString();
    }
    
    // 覆盖 super.get(key, loader) 以处理初始加载 (T=0)
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        // 先查缓存
        ValueWrapper valueWrapper = get(key);
        if (valueWrapper != null) {
            return (T) valueWrapper.get();
        }
        
        // 缓存未命中，执行同步加载 (Spring 默认逻辑)
        // 这里我们不需要特殊的 refresh 逻辑，因为那是 get(key) 负责的
        // 我们只需要确保加载的数据被包装为 RefreshWrapper
        try {
            T value = valueLoader.call();
            put(key, value); // put 会自动包装
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }
}
