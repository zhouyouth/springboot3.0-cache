package com.cache.springboot3cache.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 缓存配置类
 * 配置Redis缓存管理器以及用于异步刷新缓存的线程池
 */
@Configuration
public class CacheConfig extends CachingConfigurerSupport {

    /**
     * 配置用于缓存异步刷新的线程池
     * 使用ThreadPoolTaskExecutor创建线程池，避免占用公共线程资源
     *
     * @return Executor 线程池实例
     */
    @Bean(name = "cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(50);
        // 队列容量
        executor.setQueueCapacity(1000);
        // 线程名前缀
        executor.setThreadNamePrefix("springboot3CacheRefresh-");
        // 拒绝策略：如果队列满了，直接丢弃任务，避免阻塞主流程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 配置自定义的Redis缓存管理器
     *
     * @param connectionFactory Redis连接工厂
     * @param cacheRefreshExecutor 异步刷新线程池
     * @return CacheManager 缓存管理器实例
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, Executor cacheRefreshExecutor) {
        // 创建无锁的RedisCacheWriter
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        
        // 配置默认的Redis缓存配置
        // key使用String序列化
        // value使用JSON序列化
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        // 返回自定义的动态Redis缓存管理器
        return new DynamicRedisCacheManager(writer, config, cacheRefreshExecutor);
    }
}
