package com.cache.springboot3cache;

import com.cache.springboot3cache.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot 应用程序入口类
 * 实现了 CommandLineRunner 接口以启动缓存测试线程
 */
@SpringBootApplication
@EnableCaching // 开启缓存支持
public class Application implements CommandLineRunner {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CacheManager cacheManager;

    // 控制测试线程运行的标志位
    private volatile boolean running = true;
    // 测试线程
    private Thread testThread;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
    }

    /**
     * 应用启动后执行的方法
     * 启动一个后台线程，每秒读取一次缓存，用于测试缓存的过期和刷新机制
     */
    @Override
    public void run(String... args) throws Exception {
        System.out.println("Using CacheManager: " + cacheManager.getClass().getName());
        System.out.println("Cache Names: " + cacheManager.getCacheNames());

        testThread = new Thread(() -> {
            while (running) {
                try {
                    // 调用缓存服务获取数据
                    System.out.println("Cache value: " + cacheService.get("test"));
                    System.out.println("Cache value: " + cacheService.get3("get3"));
                    System.out.println("Cache value: " + cacheService.getAsync("test_Async"));
                    System.out.println("Cache value: " + cacheService.getNoFresh("test_NoFresh"));
                    System.out.println("Cache value: " + cacheService.getNoFreshNoSync("test_NoFreshNoSync"));
//                    System.out.println("Cache value: " + cacheService.getNoFreshNoSync("test_NoFreshNoSync"));
                    // 休眠1秒
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Test thread interrupted");
                    break; // 中断后退出循环
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("destroyed")) {
                        System.out.println("Redis connection factory destroyed, stopping test thread.");
                        break;
                    }
                    // 打印其他IllegalStateException
                    e.printStackTrace();
                } catch (Exception e) {
                    // 打印所有其他异常的完整堆栈信息
                    System.out.println("An error occurred in the test thread, stopping. Exception details:");
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("Test thread stopped");
        });
        testThread.setName("CacheTestThread");
        testThread.start();
    }

    /**
     * 应用销毁前执行的方法
     * 优雅停止测试线程
     */
    @PreDestroy
    public void onDestroy() {
        running = false;
        if (testThread != null) {
            testThread.interrupt();
            try {
                // 等待线程结束，最多等待2秒
                testThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
