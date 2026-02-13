package com.cache.springboot3cache.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 用于执行异步缓存刷新的线程池
     */
    @Bean
    public Executor cacheRefreshExecutor() {
        return Executors.newFixedThreadPool(5);
    }

    @Bean
    public RedisCacheManager customRedisCacheManager(RedisConnectionFactory redisConnectionFactory, Executor cacheRefreshExecutor) {
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);

        // 关键修复：配置正确的 Key 和 Value 序列化器
        // Key 使用 String 序列化器
        // Value 使用 JdkSerializationRedisSerializer，以便能正确序列化/反序列化 RefreshWrapper 对象
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer(getClass().getClassLoader())));

        return new RedisCacheManager(redisCacheWriter, defaultCacheConfig) {
            @Override
            protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
                String[] parts = name.split("#");
                String cacheName = parts[0];

                // 解析 name#ttl#refresh 格式
                if (parts.length > 2) {
                    Duration ttl = Duration.ofSeconds(Long.parseLong(parts[1]));
                    long refreshInSeconds = Long.parseLong(parts[2]);

                    // 应用解析出的 TTL
                    RedisCacheConfiguration newConfig = cacheConfig.entryTtl(ttl);
                    return new CustomRedisCache(cacheName, redisCacheWriter, newConfig, refreshInSeconds, cacheRefreshExecutor);
                }
                
                // 补充：解析 name#ttl 格式 (例如: test#60)
                if (parts.length > 1) {
                    Duration ttl = Duration.ofSeconds(Long.parseLong(parts[1]));
                    return super.createRedisCache(cacheName, cacheConfig.entryTtl(ttl));
                }

                return super.createRedisCache(name, cacheConfig);
            }
        };
    }
}