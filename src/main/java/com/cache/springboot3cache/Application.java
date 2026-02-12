package com.cache.springboot3cache;

import com.cache.springboot3cache.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableCaching
public class Application implements CommandLineRunner {

    @Autowired
    private CacheService cacheService;

    private volatile boolean running = true;
    private Thread testThread;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        testThread = new Thread(() -> {
            while (running) {
                try {
                    System.out.println("Cache value: " + cacheService.get("test"));
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Test thread interrupted");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Test thread stopped");
        });
        testThread.setName("CacheTestThread");
        testThread.start();
    }

    @PreDestroy
    public void onDestroy() {
        running = false;
        if (testThread != null) {
            testThread.interrupt();
            try {
                testThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
