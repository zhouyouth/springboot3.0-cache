package com.cache.springboot3cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TestCacheService {
    private final AtomicInteger basicCounter = new AtomicInteger(0);
    private final AtomicInteger expireOnlyCounter = new AtomicInteger(0);
    private final AtomicInteger refresh6_1Counter = new AtomicInteger(0);
    private final AtomicInteger refresh6_2Counter = new AtomicInteger(0);
    private final AtomicInteger refresh6_6Counter = new AtomicInteger(0);
    private final AtomicInteger refresh15_1Counter = new AtomicInteger(0);

    public TestCacheService() {
        System.out.println("TestCacheService created: " + System.identityHashCode(this));
    }

    // 移除 key = "#key"，使用全局自定义 KeyGenerator
    // 预期Key: TestCacheService:getBasic:key
    @Cacheable(cacheNames = "basic")
    public String getBasic(String key) {
        System.out.println("Executing getBasic for key: " + key + ", this: " + System.identityHashCode(this));
        basicCounter.incrementAndGet();
        return "basic-" + UUID.randomUUID();
    }

    @Cacheable(cacheNames = "test#2")
    public String getExpireOnly(String key) {
        System.out.println("Executing getExpireOnly for key: " + key + ", this: " + System.identityHashCode(this));
        expireOnlyCounter.incrementAndGet();
        return "expire-" + UUID.randomUUID();
    }

    @Cacheable(cacheNames = "test#6#1", sync = true)
    public String getRefresh6_1(String key) {
        System.out.println("Executing getRefresh6_1 for key: " + key + ", this: " + System.identityHashCode(this));
        refresh6_1Counter.incrementAndGet();
        return "6_1-" + UUID.randomUUID();
    }

    // 使用显式Key，排除KeyGenerator的问题
    @Cacheable(cacheNames = "test#6#2", key = "#key", sync = true)
    public String getRefresh6_2(String key) {
        System.out.println("Executing getRefresh6_2 for key: " + key + ", this: " + System.identityHashCode(this));
        refresh6_2Counter.incrementAndGet();
        return "6_2-" + UUID.randomUUID();
    }

    // 使用显式Key，排除KeyGenerator的问题
    @Cacheable(cacheNames = "test#6#6", key = "#key", sync = true)
    public String getRefresh6_6(String key) {
        System.out.println("Executing getRefresh6_6 for key: " + key + ", this: " + System.identityHashCode(this));
        refresh6_6Counter.incrementAndGet();
        return "6_6-" + UUID.randomUUID();
    }

    // Simulate test3#15#1 but faster: test3#5#1 (Refresh at 4s)
    @Cacheable(cacheNames = "test3#5#1", sync = true)
    public String getRefresh15_1(String key) {
        System.out.println("Executing getRefresh15_1 for key: " + key + ", this: " + System.identityHashCode(this));
        refresh15_1Counter.incrementAndGet();
        return "15_1-" + UUID.randomUUID();
    }

    public int getCounter(String type) {
        switch (type) {
            case "basic": return basicCounter.get();
            case "expireOnly": return expireOnlyCounter.get();
            case "refresh6_1": return refresh6_1Counter.get();
            case "refresh6_2": return refresh6_2Counter.get();
            case "refresh6_6": return refresh6_6Counter.get();
            case "refresh15_1": return refresh15_1Counter.get();
            default: return 0;
        }
    }
}
