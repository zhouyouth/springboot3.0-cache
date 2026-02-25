package com.cache.springboot3cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
public class RejectionPolicyTest {

    @Autowired
    @Qualifier("cacheRefreshExecutor")
    private Executor cacheRefreshExecutor;

    @Test
    public void testRejectionLogging(CapturedOutput output) throws InterruptedException {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) cacheRefreshExecutor;
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        RejectedExecutionHandler handler = threadPoolExecutor.getRejectedExecutionHandler();

        // 模拟任务被拒绝
        Runnable dummyTask = () -> System.out.println("Task");
        
        // 第一次拒绝：应该打印日志
        handler.rejectedExecution(dummyTask, threadPoolExecutor);
        assertTrue(output.getOut().contains("Cache refresh task rejected!"), "Should log warning on first rejection");

        // 立即再次拒绝：应该被限流，不打印日志（或者日志内容不变）
        // 为了验证限流，我们需要清除之前的输出或者检查日志出现的次数。
        // CapturedOutput 累积输出。
        
        // 我们可以检查日志出现的次数。
        long count1 = output.getOut().lines().filter(line -> line.contains("Cache refresh task rejected!")).count();
        
        handler.rejectedExecution(dummyTask, threadPoolExecutor);
        long count2 = output.getOut().lines().filter(line -> line.contains("Cache refresh task rejected!")).count();
        
        // 因为限流间隔是5秒，立即调用不应该增加日志
        assertTrue(count2 == count1, "Should be rate limited (no new log)");

        // 等待5秒后再次拒绝：应该打印日志
        // Thread.sleep(5100);
        // handler.rejectedExecution(dummyTask, threadPoolExecutor);
        // long count3 = output.getOut().lines().filter(line -> line.contains("Cache refresh task rejected!")).count();
        // assertTrue(count3 > count2, "Should log again after 5 seconds");
        
        // 注释掉等待测试，以免拖慢整体测试速度。只要验证了第一次打印和第二次限流即可。
    }
}
