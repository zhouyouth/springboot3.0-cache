package com.cache.springboot3cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DynamicCacheManager implements CacheManager {

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createCache);
    }

    private Cache createCache(String name) {
        String[] parts = name.split("#");
        String cacheName = parts[0];

        if (parts.length > 2) {
            long expire = Long.parseLong(parts[1]);
            long refresh = Long.parseLong(parts[2]);

            StatefulCacheLoader cacheLoader = new StatefulCacheLoader();
            Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                    .expireAfterWrite(expire, TimeUnit.SECONDS)
                    .refreshAfterWrite(refresh, TimeUnit.SECONDS);

            return new StatefulCaffeineCache(cacheName, caffeine.build(cacheLoader), cacheLoader);
        } else if (parts.length > 1) {
            long expire = Long.parseLong(parts[1]);
            Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                    .expireAfterWrite(expire, TimeUnit.SECONDS);
            return new CaffeineCache(cacheName, caffeine.build());
        } else {
            return new CaffeineCache(name, Caffeine.newBuilder().build());
        }
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }
}
