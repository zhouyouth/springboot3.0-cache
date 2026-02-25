package com.cache.springboot3cache.config;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    // 使用 ObjectProvider 延迟获取 Bean，解决循环依赖问题，比 @Lazy 更优雅
    private final ObjectProvider<CacheResolver> cacheResolverProvider;

    public CacheConfig(ObjectProvider<CacheResolver> cacheResolverProvider) {
        this.cacheResolverProvider = cacheResolverProvider;
    }

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
        
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            private final AtomicLong lastLogTime = new AtomicLong(0);
            private static final long LOG_INTERVAL_MS = 5000;

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                long now = System.currentTimeMillis();
                long last = lastLogTime.get();
                if (now - last > LOG_INTERVAL_MS) {
                    if (lastLogTime.compareAndSet(last, now)) {
                        // logger is not static here, need to get it or use System.err
                        // Using System.err for simplicity in anonymous class or LoggerFactory
                        LoggerFactory.getLogger(CacheConfig.class).warn("Cache refresh task rejected! Queue capacity exceeded. Task: {}", r.toString());
                    }
                }
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
        return cacheResolverProvider.getIfAvailable();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return null;
    }
}
