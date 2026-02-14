package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
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
    // 锁的后缀
    private static final String LOCK_SUFFIX = "~lock";
    
    // 分段锁数组，用于减少 synchronized (this) 的竞争
    private final Object[] keyLocks;
    // 分段锁数量
    private static final int LOCK_STRIPE_COUNT = 64;

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
        
        // 初始化分段锁
        this.keyLocks = new Object[LOCK_STRIPE_COUNT];
        for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
            this.keyLocks[i] = new Object();
        }
        logger.info("CustomRedisCache created: name={}, refreshInSeconds={}", name, refreshInSeconds);
    }

    /**
     * 重写lookup方法
     * 增加对缓存值类型的检查，如果不是RefreshWrapper类型，视为无效缓存并清除
     */
    @Override
    protected Object lookup(Object key) {
        Object value = super.lookup(key);
        if (value != null && !(value instanceof RefreshWrapper)) {
            logger.warn("Invalid cache entry for key {}. Found type: {}. Evicting.", key, value.getClass().getName());
            evict(key);
            return null;
        }
        return value;
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
         logger.info("get(key) called for key: {}", key);
        Object value = lookup(key);

        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            // 检查是否超过刷新时间
            if (System.currentTimeMillis() - wrapper.getCreateTime() > refreshInSeconds * 1000) {
                // 策略优化：
                // 尝试获取锁，只有一个线程能获取成功
                // 1. 获取到锁的线程返回 null -> 触发 Spring 同步调用目标方法更新缓存
                // 2. 未获取到锁的线程返回旧值 -> 降级服务，避免缓存击穿和阻塞
                byte[] lockKey = createLockKey(key);
                boolean locked = false;
                try {
                    locked = tryLock(lockKey, "1".getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    logger.error("Failed to acquire lock for key: {}", key, e);
                }

                if (locked) {
                    logger.info("Cache key {} is stale. Acquired lock, returning null to trigger sync refresh.", key);
                    return null;
                }
                logger.info("Cache key {} is stale. Lock busy, returning stale value.", key);
                return new SimpleValueWrapper(wrapper.getValue());
            }
            return new SimpleValueWrapper(wrapper.getValue());
        }
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    /**
     * 获取缓存值（带加载器）
     * 如果缓存值是RefreshWrapper类型，检查是否需要刷新
     * 如果需要刷新，尝试获取分布式锁，获取成功则异步刷新，否则返回旧值
     *
     * @param key 缓存键
     * @param valueLoader 值加载器
     * @param <T> 值类型
     * @return T 缓存值
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        logger.info("get(key, loader) called for key: {}", key);
        Object value = lookup(key);

        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            long age = System.currentTimeMillis() - wrapper.getCreateTime();
            logger.info("Cache hit for key: {}, age: {}ms, refresh: {}ms", key, age, refreshInSeconds * 1000);

            if (age > refreshInSeconds * 1000) {
                logger.info("Cache key {} is stale (age={}ms > {}ms), attempting refresh...", key, age, refreshInSeconds * 1000);

                String lockValue = UUID.randomUUID().toString();
                byte[] lockKey = createLockKey(key);

                // 尝试获取分布式锁
                boolean locked = false;
                try {
                    locked = tryLock(lockKey, lockValue.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    logger.error("Failed to acquire lock for key: {}", key, e);
                }

                if (locked) {
                    logger.info("Acquired lock, async refreshing cache key: {}", key);
                    // 获取锁成功，异步刷新
                    CompletableFuture.runAsync(() -> {
                        try {
                            logger.info("Executing refresh for key: {} in thread: {}", key, Thread.currentThread().getName());
                            T newValue = valueLoader.call();
                            put(key, newValue);
                        } catch (Exception e) {
                            logger.error("Error refreshing cache key: {}", key, e);
                        } finally {
                            // 刷新完成后释放锁
                            try {
                                releaseLock(lockKey, lockValue.getBytes(StandardCharsets.UTF_8));
                            } catch (Exception e) {
                                logger.error("Failed to release lock for key: {}", key, e);
                            }
                        }
                    }, executor);
                } else {
                    logger.info("Failed to acquire lock for key: {}, another thread is refreshing or lock error.", key);
                }

                // 无论是否获取到锁，都返回旧值，实现Refresh-Ahead
                return (T) wrapper.getValue();
            }
            return (T) wrapper.getValue();
        }

        // 修复：super.get(key, valueLoader) 可能没有调用我们重写的 put 方法，导致数据未被 RefreshWrapper 包装
        // 这里手动实现同步加载和写入逻辑，确保 put 被调用。
        // 使用分段锁代替 synchronized(this)，避免锁住整个缓存实例导致不同 Key 无法并发加载
        int lockIndex = (key.hashCode() & 0x7FFFFFFF) % LOCK_STRIPE_COUNT;
        
        synchronized (keyLocks[lockIndex]) {
            Object v2 = lookup(key);
            if (v2 instanceof RefreshWrapper) {
                return (T) ((RefreshWrapper) v2).getValue();
            }

            T loadedValue;
            try {
                loadedValue = valueLoader.call();
            } catch (Exception e) {
                throw new RuntimeException("Value loader failed", e);
            }
            put(key, loadedValue);
            return loadedValue;
        }
    }

    /**
     * 尝试获取锁
     * 使用RedisCacheWriter.putIfAbsent，它会自动处理key前缀
     */
    private boolean tryLock(byte[] key, byte[] value) {
        // putIfAbsent 返回 null 表示设置成功（获取到锁）
        // 设置10秒过期
        return getNativeCache().putIfAbsent(getName(), key, value, Duration.ofSeconds(10)) == null;
    }

    /**
     * 释放锁
     * 检查值是否匹配，匹配则删除
     */
    private void releaseLock(byte[] key, byte[] value) {
        byte[] existing = getNativeCache().get(getName(), key);
        if (Arrays.equals(existing, value)) {
            getNativeCache().remove(getName(), key);
        }
    }

    /**
     * 创建锁的Key
     * 使用配置的Key序列化器进行序列化
     */
    private byte[] createLockKey(Object key) {
        // 使用RedisCacheConfiguration中的KeySerializationPair进行序列化
        ByteBuffer keyBuffer = getCacheConfiguration().getKeySerializationPair().write(convertKey(key));
        ByteBuffer suffixBuffer = getCacheConfiguration().getKeySerializationPair().write(LOCK_SUFFIX);
        byte[] lockSuffixBytes = new byte[suffixBuffer.remaining()];
        suffixBuffer.get(lockSuffixBytes);

        // 合并 key 的序列化结果和锁后缀
        byte[] lockKeyBytes = new byte[keyBuffer.remaining() + lockSuffixBytes.length];
        keyBuffer.get(lockKeyBytes, 0, keyBuffer.remaining());
        System.arraycopy(lockSuffixBytes, 0, lockKeyBytes, lockKeyBytes.length - lockSuffixBytes.length, lockSuffixBytes.length);

        // 注意：这里直接返回了合并后的 byte[]，但 Spring 的 RedisCacheWriter 会自动添加前缀。
        // 我们需要确保这个组合键不会与普通缓存键冲突，追加后缀的方式是安全的。
        return lockKeyBytes;
    }

    /**
     * 存入缓存
     */
    @Override
    public void put(Object key, Object value) {
        logger.info("put called for key: {}", key);
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        super.put(key, value);

        // 修复并发问题：更新缓存后，主动释放（删除）锁，以便下一次刷新能及时进行
        try {
            byte[] lockKey = createLockKey(key);
            getNativeCache().remove(getName(), lockKey);
        } catch (Exception e) {
            logger.error("Failed to release lock in put for key: {}", key, e);
        }
    }

    /**
     * 如果不存在则存入缓存
     */
    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        if (!(value instanceof RefreshWrapper)) {
            value = new RefreshWrapper(value, System.currentTimeMillis());
        }
        ValueWrapper result = super.putIfAbsent(key, value);
        
        // 同样需要在 putIfAbsent 成功后尝试清理锁（虽然 putIfAbsent 通常用于初始化，但保持一致性更好）
        try {
            byte[] lockKey = createLockKey(key);
            getNativeCache().remove(getName(), lockKey);
        } catch (Exception e) {
            logger.error("Failed to release lock in putIfAbsent for key: {}", key, e);
        }
        return result;
    }
}
