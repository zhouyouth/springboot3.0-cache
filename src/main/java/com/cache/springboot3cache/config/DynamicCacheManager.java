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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicCacheManager implements CacheManager {

    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private static final Pattern PATTERN = Pattern.compile("(.+)#(\\d+)#(\\d+)");

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createCache);
    }

    private Cache createCache(String name) {
        Matcher matcher = PATTERN.matcher(name);
        if (matcher.find()) {
            String cacheName = matcher.group(1);
            long expire = Long.parseLong(matcher.group(2));
            long refresh = Long.parseLong(matcher.group(3));

            StatefulCacheLoader cacheLoader = new StatefulCacheLoader();
            Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                    .expireAfterWrite(expire, TimeUnit.SECONDS)
                    .refreshAfterWrite(refresh, TimeUnit.SECONDS);

            return new StatefulCaffeineCache(cacheName, caffeine.build(cacheLoader), cacheLoader);
        } else {
            return new CaffeineCache(name, Caffeine.newBuilder().build());
        }
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }
}
