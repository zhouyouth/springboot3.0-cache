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
     * test666#6#3 代表：
     * 缓存名称：test666
     * 过期时间：6秒
     * 刷新时间：3秒（超过3秒后访问会触发异步刷新）
     *
     * @param key 缓存键
     * @return 缓存值
     */
    @Override
    @Cacheable(cacheNames = "test666#6#3", key = "#key", sync = true)
    public String get(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "test" + atomicInteger.incrementAndGet();
    }
}
