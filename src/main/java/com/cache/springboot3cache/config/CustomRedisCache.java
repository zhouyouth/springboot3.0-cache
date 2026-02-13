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

/**
 * 自定义RedisCache实现，支持缓存自动刷新
 * 继承自Spring Data Redis的RedisCache
 */
public class CustomRedisCache extends RedisCache {

    private static final Logger logger = LoggerFactory.getLogger(CustomRedisCache.class);
    // 刷新时间间隔（秒）
    private final long refreshInSeconds;
    // 用于执行异步刷新任务的线程池
    private final Executor executor;

    /**
     * 构造函数
     *
     * @param name 缓存名称
     * @param cacheWriter Redis缓存写入器
     * @param cacheConfig Redis缓存配置
     * @param refreshInSeconds 刷新时间间隔（秒）
     * @param executor 异步刷新线程池
     */
    protected CustomRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, long refreshInSeconds, Executor executor) {
        super(name, cacheWriter, cacheConfig);
        this.refreshInSeconds = refreshInSeconds;
        this.executor = executor;
    }

    /**
     * 获取缓存值
     * 如果缓存值是RefreshWrapper类型，检查是否需要刷新
     * 如果需要刷新且没有valueLoader（即sync=false），返回null强制同步刷新
     *
     * @param key 缓存键
     * @return ValueWrapper 缓存值包装器
     */
    @Override
    public ValueWrapper get(Object key) {
        Object value = lookup(key);
        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            // 检查是否超过刷新时间
            if (System.currentTimeMillis() - wrapper.getCreateTime() > refreshInSeconds * 1000) {
                return null; // 返回null，强制Spring进行同步加载并更新缓存
            }
            return new SimpleValueWrapper(wrapper.getValue());
        }
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    /**
     * 获取缓存值（带加载器）
     * 如果缓存值是RefreshWrapper类型，检查是否需要刷新
     * 如果需要刷新，使用线程池异步执行valueLoader加载新值，并返回旧值
     *
     * @param key 缓存键
     * @param valueLoader 值加载器
     * @param <T> 值类型
     * @return T 缓存值
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            // 检查是否超过刷新时间
            if (System.currentTimeMillis() - wrapper.getCreateTime() > refreshInSeconds * 1000) {
                // 使用专用线程池进行异步刷新
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
                // 返回旧值，实现Refresh-Ahead
                return (T) wrapper.getValue();
            }
            return (T) wrapper.getValue();
        }
        return super.get(key, valueLoader);
    }

    /**
     * 存入缓存
     * 将值包装为RefreshWrapper，记录创建时间
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    @Override
    public void put(Object key, Object value) {
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        super.put(key, value);
    }

    /**
     * 如果不存在则存入缓存
     * 将值包装为RefreshWrapper，记录创建时间
     *
     * @param key 缓存键
     * @param value 缓存值
     * @return ValueWrapper 缓存值包装器
     */
    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        return super.putIfAbsent(key, value);
    }
}
