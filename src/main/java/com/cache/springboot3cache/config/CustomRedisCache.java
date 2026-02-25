package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.cache.Cache;
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
    private final RedisTemplate<Object, Object> redisTemplate;
    private final CacheOperationInvocationContext<?> context;
    private static final String LOCK_SUFFIX = "~lock";

    protected CustomRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, long refreshInSeconds, Executor executor, StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate, CacheOperationInvocationContext<?> context) {
        super(name, cacheWriter, cacheConfig);
        this.refreshInSeconds = refreshInSeconds;
        this.executor = executor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.context = context;
    }

    @Override
    public ValueWrapper get(Object key) {
        String redisKey = createKey(key);
        Object value = redisTemplate.opsForValue().get(redisKey);

        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            
            if (refreshInSeconds >= 0) {
                long age = System.currentTimeMillis() - wrapper.getCreateTime();
                if (age > refreshInSeconds * 1000) {
                    refreshAsync(key);
                }
            }
            
            return new SimpleValueWrapper(wrapper.getValue());
        }
        
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    private void refreshAsync(Object key) {
        String lockKey = createLockKey(key);
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
        
        if (Boolean.TRUE.equals(locked)) {
            CompletableFuture.runAsync(() -> {
                try {
                    Object target = context.getTarget();
                    Object rawTarget = AopProxyUtils.getSingletonTarget(target);
                    if (rawTarget == null) {
                        rawTarget = target;
                    }

                    Object[] args = context.getArgs();
                    Object newValue = context.getMethod().invoke(rawTarget, args);

                    put(key, newValue);
                    
                } catch (Exception e) {
                    logger.error("Error refreshing cache key: {}", key, e);
                } finally {
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
        
        String redisKey = createKey(key);
        Duration ttl = getCacheConfiguration().getTtl();
        redisTemplate.opsForValue().set(redisKey, wrapper, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        put(key, value);
        return new SimpleValueWrapper(value);
    }
    
    private String createLockKey(Object key) {
        return getName() + "::" + key.toString() + LOCK_SUFFIX;
    }
    
    private String createKey(Object key) {
        return getName() + "::" + key.toString();
    }
    
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper valueWrapper = get(key);
        if (valueWrapper != null) {
            return (T) valueWrapper.get();
        }
        
        synchronized (this) {
            valueWrapper = get(key);
            if (valueWrapper != null) {
                return (T) valueWrapper.get();
            }

            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }
    }
}
