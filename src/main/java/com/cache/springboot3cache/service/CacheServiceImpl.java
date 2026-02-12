package com.cache.springboot3cache.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CacheServiceImpl implements CacheService {

    private final AtomicInteger atomicInteger = new AtomicInteger();

    @Override
    @Cacheable(cacheNames = "test666#6#3", key = "#key")
    public String get(String key) {
        System.out.println("get from method");
        return "test";
        //return "test" + atomicInteger.incrementAndGet();
    }
}
