package com.cache.springboot3cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CustomRedisCache extends RedisCache {

    private static final Logger logger = LoggerFactory.getLogger(CustomRedisCache.class);
    private final long refreshInSeconds;
    private final Executor executor;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheOperationInvocationContext<?> context;
    private static final String LOCK_SUFFIX = "~lock";
    private final JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();

    protected CustomRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, long refreshInSeconds, Executor executor, StringRedisTemplate stringRedisTemplate, CacheOperationInvocationContext<?> context) {
        super(name, cacheWriter, cacheConfig);
        this.refreshInSeconds = refreshInSeconds;
        this.executor = executor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.context = context;
        System.err.println("CustomRedisCache created: name=" + name + ", refreshInSeconds=" + refreshInSeconds + ", this=" + System.identityHashCode(this));
    }

    @Override
    protected Object lookup(Object key) {
        // Bypass super.lookup and read directly
        String redisKey = createKey(key);
        byte[] bytes = stringRedisTemplate.getConnectionFactory().getConnection().get(redisKey.getBytes());
        
        if (bytes == null) {
            System.err.println("lookup(" + key + ") -> null");
            return null;
        }
        
        try {
            Object value = serializer.deserialize(bytes);
            System.err.println("lookup(" + key + ") returned type: " + (value != null ? value.getClass().getName() : "null") + ", this=" + System.identityHashCode(this));
            return value;
        } catch (Exception e) {
            System.err.println("lookup(" + key + ") deserialize failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = lookup(key);
        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            long age = System.currentTimeMillis() - wrapper.getCreateTime();
            if (age > refreshInSeconds * 1000) {
                System.err.println("get(key): Cache key " + key + " is stale (age=" + age + "ms > " + refreshInSeconds * 1000 + "ms), returning NULL");
                return null; 
            }
            return new SimpleValueWrapper(wrapper.getValue());
        }
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        System.err.println("get(key, loader) called for key: " + key + ", this=" + System.identityHashCode(this));
        Object value = lookup(key);
        
        if (value == null) {
            System.err.println("get(key, loader): Cache miss for key: " + key);
            // Force put after super.get returns
            T loadedValue = super.get(key, valueLoader);
            System.err.println("super.get returned: " + loadedValue);
            put(key, loadedValue);
            return loadedValue;
        }

        if (value instanceof RefreshWrapper) {
            RefreshWrapper wrapper = (RefreshWrapper) value;
            long age = System.currentTimeMillis() - wrapper.getCreateTime();
            System.err.println("get(key, loader): Cache hit for key: " + key + ", age: " + age + "ms, refresh: " + refreshInSeconds * 1000 + "ms");
            
            if (age > refreshInSeconds * 1000) {
                System.err.println("get(key, loader): Triggering async refresh for key: " + key);
                
                String lockKey = createLockKey(key);
                String lockValue = UUID.randomUUID().toString();
                
                boolean locked = false;
                try {
                    locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10)));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (locked) {
                    System.err.println("Acquired lock, async refreshing cache key: " + key);
                    CompletableFuture.runAsync(() -> {
                        try {
                            System.err.println("Executing refresh for key: " + key + " in thread: " + Thread.currentThread().getName());
                            
                            Object target = context.getTarget();
                            Object rawTarget = AopProxyUtils.getSingletonTarget(target);
                            if (rawTarget == null) {
                                rawTarget = target;
                            }
                            
                            Object[] args = context.getArgs();
                            Object newValue = context.getMethod().invoke(rawTarget, args);
                            
                            System.err.println("Refreshed value for key " + key + ": " + newValue);
                            put(key, newValue);
                            System.err.println("Put new value for key " + key + " done.");
                            
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
                                if (lockValue.equals(currentValue)) {
                                    stringRedisTemplate.delete(lockKey);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, executor);
                } else {
                    System.err.println("Failed to acquire lock for key: " + key);
                }
                return (T) wrapper.getValue();
            }
            return (T) wrapper.getValue();
        } else {
            System.err.println("Value for key " + key + " is NOT RefreshWrapper: " + (value != null ? value.getClass().getName() : "null"));
        }
        return super.get(key, valueLoader);
    }

    private String createLockKey(Object key) {
        return getName() + "::" + key.toString() + LOCK_SUFFIX;
    }
    
    private String createKey(Object key) {
        return getName() + "::" + key.toString();
    }

    @Override
    public void put(Object key, Object value) {
        System.err.println("put called for key: " + key + ", value type: " + (value != null ? value.getClass().getName() : "null") + ", this=" + System.identityHashCode(this));
        
        RefreshWrapper wrapper;
        if (value instanceof RefreshWrapper) {
            wrapper = (RefreshWrapper) value;
        } else {
            wrapper = new RefreshWrapper(value, System.currentTimeMillis());
            System.err.println("Wrapped value in RefreshWrapper");
        }
        
        // Manual put
        String redisKey = createKey(key);
        byte[] bytes = serializer.serialize(wrapper);
        Duration ttl = getCacheConfiguration().getTtl();
        
        stringRedisTemplate.getConnectionFactory().getConnection().set(redisKey.getBytes(), bytes, Expiration.from(ttl.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS), org.springframework.data.redis.connection.RedisStringCommands.SetOption.upsert());
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        // Similar manual implementation needed if used
        return super.putIfAbsent(key, value);
    }
}
