package com.cache.springboot3cache.service;

import org.springframework.cache.annotation.Cacheable;

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

    String getAsync(String key);

    @Cacheable(cacheNames = "testAsync2#9#6", key = "#key", sync = true)
    String getNoFresh(String key);

    @Cacheable(cacheNames = "testNoFresh#12", key = "#key", sync = false)
    String getNoFresh2(String key);

    @Cacheable(cacheNames = "testNoFreshNoSync#12", key = "#key", sync = false)
    String getNoFreshNoSync(String key);
}
