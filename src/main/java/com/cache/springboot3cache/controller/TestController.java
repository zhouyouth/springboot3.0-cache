package com.cache.springboot3cache.controller;

import com.cache.springboot3cache.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器
 * 提供HTTP接口测试缓存功能
 */
@RestController
public class TestController {

    @Autowired
    private CacheService cacheService;

    /**
     * 测试接口
     * 访问 /test 触发缓存读取
     *
     * @return 缓存值
     */
    @GetMapping("/test")
    public String test() {
        return cacheService.get("test");
    }
}
