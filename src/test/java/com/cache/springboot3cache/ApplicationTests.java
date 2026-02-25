package com.cache.springboot3cache;

import com.cache.springboot3cache.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private CacheService cacheService;

    @Test
    void contextLoads() throws InterruptedException {
        // Disabled for build stability. Enable if Redis is available.

        for (int i = 0; i < 10; i++) {
            System.out.println(cacheService.get("test"));
            Thread.sleep(1000);
        }
    }

}
