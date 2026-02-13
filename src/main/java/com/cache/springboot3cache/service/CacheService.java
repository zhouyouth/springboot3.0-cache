package com.cache.springboot3cache.service;

/**
 * 缓存服务接口
 */
public interface CacheService {
    /**
     * 获取缓存数据
     * @param key 缓存键
     * @return 缓存值
     */
    String get(String key);
}
