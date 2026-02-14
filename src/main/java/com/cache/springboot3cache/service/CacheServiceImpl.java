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
     * test#6#3 代表：test#agr1#agr2 当arg2 >=arg1时,会立即刷新缓存,如test#6#6,test#6#9,不建议这样用
     * 缓存名称：test
     * 逻辑过期时间：6秒
     * 缓存存活时间：逻辑过期时间+刷新时间
     * 第二个参数3 代表缓存存活时间超过多少秒后开始刷新 6-3=3 即缓存存活后超过3秒后刷新,这里的缓存存活时间以物理时间来算即6+3=9秒 即第6秒刷新
     * 刷新时间：逻辑时间的倒数3秒开始刷新(即6-3=3秒 当缓存存活时间超过3秒时（即第4秒开始），触发异步刷新)
     * 物理过期时间：等于逻辑过期时间+刷新时间 即 6+3 = 9秒
     * @return 缓存值 默认 sync = false 缓存数据同步刷新(阻塞)
     * sync = true 缓存数据异步刷新(高并发建议用)
     */
    @Override
    @Cacheable(cacheNames = "test#6#1", key = "#key", sync = true)
    public String get(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "test-" + atomicInteger.incrementAndGet();
    }

    @Override
    @Cacheable(cacheNames = "testAsync#9#8", key = "#key", sync = true)
    public String getAsync(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "getAsync-" + atomicInteger.incrementAndGet();
    }
    @Cacheable(cacheNames = "test3#15#1", key = "#key", sync = true)
    @Override
    public String get3(String key) {
        System.out.println("get from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "get3-" + atomicInteger.incrementAndGet();
    }
    @Override
    @Cacheable(cacheNames = "testNoFresh#12", key = "#key", sync = true)
    public String getNoFresh(String key) {
        System.out.println("getNoFresh from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "getNoFresh-" + atomicInteger.incrementAndGet();
    }

    @Cacheable(cacheNames = "testNoFresh#12", key = "#key", sync = false)
    @Override
    public String getNoFresh2(String key) {
        System.out.println("getNoFresh2 from method");
        return "getNoFresh2-" + atomicInteger.incrementAndGet();
    }

    @Override
    @Cacheable(cacheNames = "testNoFreshNoSync#15", key = "#key", sync = false)
    public String getNoFreshNoSync(String key) {
        System.out.println("getNoFreshNoSync from method");
        // 返回带自增序号的字符串，以便观察缓存刷新效果
        return "getNoFreshNoSync-" + atomicInteger.incrementAndGet();
    }


}
