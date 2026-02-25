package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Autowired
    @Lazy
    private CacheResolver myCacheResolver;

    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }

    @Bean(name = "cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("springboot3CacheRefresh-");
        
        // 自定义拒绝策略：记录日志并丢弃，带简单的限流防止日志爆炸
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            private final AtomicLong lastLogTime = new AtomicLong(0);
            private static final long LOG_INTERVAL_MS = 5000; // 每5秒最多打印一次

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                long now = System.currentTimeMillis();
                long last = lastLogTime.get();
                if (now - last > LOG_INTERVAL_MS) {
                    if (lastLogTime.compareAndSet(last, now)) {
                        logger.warn("Cache refresh task rejected! Queue capacity exceeded. Task: {}", r.toString());
                    }
                }
                // 依然执行丢弃策略，不抛出异常
            }
        });
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return new RedisCacheManager(writer, config);
    }

    @Bean
    public CacheResolver myCacheResolver(CacheManager cacheManager, RedisConnectionFactory connectionFactory, Executor cacheRefreshExecutor, StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return new MyCacheResolver(cacheManager, writer, config, cacheRefreshExecutor, stringRedisTemplate, redisTemplate);
    }

    @Override
    public CacheResolver cacheResolver() {
        logger.info("Providing custom CacheResolver");
        return myCacheResolver;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return null;
    }
}
