package com.cache.springboot3cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.concurrent.Callable;

public class StatefulCaffeineCache extends CaffeineCache {

    private final StatefulCacheLoader cacheLoader;

    public StatefulCaffeineCache(String name, Cache<Object, Object> cache, StatefulCacheLoader cacheLoader) {
        super(name, cache, true);
        this.cacheLoader = cacheLoader;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        cacheLoader.addValueLoader(key, valueLoader);
        return super.get(key, valueLoader);
    }

    @Override
    public void evict(Object key) {
        super.evict(key);
        cacheLoader.removeValueLoader(key);
    }

    @Override
    public void clear() {
        super.clear();
    }
}
