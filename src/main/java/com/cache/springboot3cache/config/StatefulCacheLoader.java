package com.cache.springboot3cache.config;

import com.github.benmanes.caffeine.cache.CacheLoader;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class StatefulCacheLoader implements CacheLoader<Object, Object> {
    private final Map<Object, Callable<?>> valueLoaderMap = new ConcurrentHashMap<>();

    @Override
    public Object load(Object key) throws Exception {
        Callable<?> valueLoader = valueLoaderMap.get(key);
        if (valueLoader != null) {
            return valueLoader.call();
        }
        return null;
    }

    public void addValueLoader(Object key, Callable<?> valueLoader) {
        valueLoaderMap.put(key, valueLoader);
    }

    public void removeValueLoader(Object key) {
        valueLoaderMap.remove(key);
    }
}
