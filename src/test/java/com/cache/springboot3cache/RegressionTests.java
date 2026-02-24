package com.cache.springboot3cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Import(TestCacheService.class)
public class RegressionTests {

    @Autowired
    private TestCacheService testCacheService;

    @Autowired
    private CacheManager cacheManager;

    private String generateKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    public void testBasicCaching() {
        String key = generateKey();
        System.out.println("Testing Basic Caching with key: " + key);
        String val1 = testCacheService.getBasic(key);
        String val2 = testCacheService.getBasic(key);
        Assertions.assertEquals(val1, val2, "Values should be equal (cached)");
    }

    @Test
    public void testExpiration() throws InterruptedException {
        // test#2: Expire 2s, No refresh
        String key = generateKey();
        System.out.println("Testing Expiration with key: " + key);
        String val1 = testCacheService.getExpireOnly(key);
        
        System.out.println("Waiting 3s for expiration...");
        TimeUnit.SECONDS.sleep(3);
        
        String val2 = testCacheService.getExpireOnly(key);
        Assertions.assertNotEquals(val1, val2, "Values should be different (expired)");
    }

    @Test
    public void testRefreshAhead_6_1() throws InterruptedException {
        // test#6#1: Expire 6s, Refresh at 5s (6-1)
        String key = generateKey();
        System.out.println("Testing Refresh 6_1 with key: " + key);
        
        // T=0
        String val0 = testCacheService.getRefresh6_1(key);
        System.out.println("Val0: " + val0);

        // T=2 (Age 2 < 5): Should hit cache, no refresh
        System.out.println("Waiting 2s...");
        TimeUnit.SECONDS.sleep(2);
        String val2 = testCacheService.getRefresh6_1(key);
        System.out.println("Val2: " + val2);
        Assertions.assertEquals(val0, val2);

        // T=5.5 (Age 5.5 > 5): Should hit cache (stale), trigger async refresh
        System.out.println("Waiting 3.5s (Total 5.5s)...");
        TimeUnit.MILLISECONDS.sleep(3500); 
        String val5 = testCacheService.getRefresh6_1(key);
        System.out.println("Val5: " + val5);
        Assertions.assertEquals(val0, val5, "Should return stale value immediately");
        
        // Wait for async refresh to complete
        System.out.println("Waiting 5s for async refresh...");
        TimeUnit.SECONDS.sleep(5);
        
        // T=10.5: Should get new value
        String val6 = testCacheService.getRefresh6_1(key);
        System.out.println("Val6: " + val6);
        System.out.println("Counter: " + testCacheService.getCounter("refresh6_1"));
        Assertions.assertNotEquals(val0, val6, "Should have refreshed value");
    }

    @Test
    public void testRefreshAhead_6_2() throws InterruptedException {
        // test#6#2: Expire 6s, Refresh at 4s (6-2)
        String key = generateKey();
        System.out.println("Testing Refresh 6_2 with key: " + key);
        
        // T=0
        String val0 = testCacheService.getRefresh6_2(key);
        System.out.println("Val0: " + val0);
        
        // T=3 (Age 3 < 4): No refresh
        System.out.println("Waiting 3s...");
        TimeUnit.SECONDS.sleep(3);
        String val3 = testCacheService.getRefresh6_2(key);
        System.out.println("Val3: " + val3);
        Assertions.assertEquals(val0, val3);

        // T=4.5 (Age 4.5 > 4): Trigger refresh
        System.out.println("Waiting 1.5s (Total 4.5s)...");
        TimeUnit.MILLISECONDS.sleep(1500);
        String val4 = testCacheService.getRefresh6_2(key);
        System.out.println("Val4: " + val4);
        Assertions.assertEquals(val0, val4, "Should return stale value");
        
        // Wait for refresh
        System.out.println("Waiting 5s for async refresh...");
        TimeUnit.SECONDS.sleep(5);
        
        String val5 = testCacheService.getRefresh6_2(key);
        System.out.println("Val5: " + val5);
        System.out.println("Counter: " + testCacheService.getCounter("refresh6_2"));
        Assertions.assertNotEquals(val0, val5);
    }

    @Test
    public void testConstraintCorrection_6_6() throws InterruptedException {
        // test#6#6: Invalid. Corrected to test#6#3. Refresh at 3s.
        String key = generateKey();
        System.out.println("Testing Constraint 6_6 with key: " + key);
        
        String val0 = testCacheService.getRefresh6_6(key);
        System.out.println("Val0: " + val0);
        
        // T=2 (Age 2 < 3): No refresh
        System.out.println("Waiting 2s...");
        TimeUnit.SECONDS.sleep(2);
        String val2 = testCacheService.getRefresh6_6(key);
        System.out.println("Val2: " + val2);
        Assertions.assertEquals(val0, val2);
        
        // T=3.5 (Age 3.5 > 3): Trigger refresh
        System.out.println("Waiting 1.5s (Total 3.5s)...");
        TimeUnit.MILLISECONDS.sleep(1500);
        testCacheService.getRefresh6_6(key);
        
        // Wait for refresh
        System.out.println("Waiting 5s for async refresh...");
        TimeUnit.SECONDS.sleep(5);
        
        String val4 = testCacheService.getRefresh6_6(key);
        System.out.println("Val4: " + val4);
        System.out.println("Counter: " + testCacheService.getCounter("refresh6_6"));
        Assertions.assertNotEquals(val0, val4);
    }

    @Test
    public void testRefreshAhead_15_1() throws InterruptedException {
        // test3#5#1: Expire 5s, Refresh at 4s (5-1)
        String key = generateKey();
        System.out.println("Testing Refresh 15_1 (simulated as 5_1) with key: " + key);
        
        // T=0
        String val0 = testCacheService.getRefresh15_1(key);
        System.out.println("Val0: " + val0);
        
        // T=3 (Age 3 < 4): No refresh
        System.out.println("Waiting 3s...");
        TimeUnit.SECONDS.sleep(3);
        String val3 = testCacheService.getRefresh15_1(key);
        System.out.println("Val3: " + val3);
        Assertions.assertEquals(val0, val3);

        // T=4.5 (Age 4.5 > 4): Trigger refresh
        System.out.println("Waiting 1.5s (Total 4.5s)...");
        TimeUnit.MILLISECONDS.sleep(1500);
        String val4 = testCacheService.getRefresh15_1(key);
        System.out.println("Val4: " + val4);
        Assertions.assertEquals(val0, val4, "Should return stale value");
        
        // Wait for refresh
        System.out.println("Waiting 5s for async refresh...");
        TimeUnit.SECONDS.sleep(5);
        
        String val5 = testCacheService.getRefresh15_1(key);
        System.out.println("Val5: " + val5);
        System.out.println("Counter: " + testCacheService.getCounter("refresh15_1"));
        Assertions.assertNotEquals(val0, val5);
    }
}
