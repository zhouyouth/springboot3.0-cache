package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CustomRedisCache extends RedisCache {

    private static final Logger logger = LoggerFactory.getLogger(CustomRedisCache.class);
    private final long refreshInSeconds;
    private final Executor executor;

    protected CustomRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, long refreshInSeconds, Executor executor) {
        super(name, cacheWriter, cacheConfig);
        this.refreshInSeconds = refreshInSeconds;
        this.executor = executor;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = lookup(key);
        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            if (System.currentTimeMillis() - wrapper.getCreateTime() > refreshInSeconds * 1000) {
                return null; // Force sync refresh
            }
            return new SimpleValueWrapper(wrapper.getValue());
        }
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            if (System.currentTimeMillis() - wrapper.getCreateTime() > refreshInSeconds * 1000) {
                // Async refresh using the dedicated executor
                logger.info("Async refreshing cache key: {}", key);
                CompletableFuture.runAsync(() -> {
                    try {
                        logger.info("Executing refresh for key: {} in thread: {}", key, Thread.currentThread().getName());
                        T newValue = valueLoader.call();
                        put(key, newValue);
                    } catch (Exception e) {
                        logger.error("Error refreshing cache key: {}", key, e);
                    }
                }, executor);
                return (T) wrapper.getValue();
            }
            return (T) wrapper.getValue();
        }
        return super.get(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        super.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        return super.putIfAbsent(key, value);
    }
}
