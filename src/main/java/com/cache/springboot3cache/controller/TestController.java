package com.cache.springboot3cache.controller;

import com.cache.springboot3cache.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private CacheService cacheService;

    @GetMapping("/test")
    public String test() {
        return cacheService.get("test");
    }
}
