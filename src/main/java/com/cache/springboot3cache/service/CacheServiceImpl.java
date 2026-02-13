package com.cache.springboot3cache.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存服务实现类
 */
@Service
public class CacheServiceImpl implements CacheService {

    private final AtomicInteger atomicInteger = new AtomicInteger();

    /**
     * 获取数据并缓存
     * cacheNames格式：name#expire#refresh
     * test#6#3 代表：
     * 缓存名称：test
     * 过期时间：6秒
     * 刷新时间：3秒（超过3秒后访问会触发异步刷新）
     *
     * @param key 缓存键
     * @return 缓存值
     * sync = true
     * 默认 sync = false 缓存数据同步刷新(阻塞)
     * sync = true 缓存数据异步刷新(高并发建议用)
     */
    @Override
    @Cacheable(cacheNames = "test#6#3", key = "#key", sync = true)
    public String get(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "test-" + atomicInteger.incrementAndGet();
    }

    @Override
    @Cacheable(cacheNames = "testAsync#9#6", key = "#key", sync = true)
    public String getAsync(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "getAsync-" + atomicInteger.incrementAndGet();
    }





}
