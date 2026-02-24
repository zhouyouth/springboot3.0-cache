package com.cache.springboot3cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 自定义全局缓存Key生成器
 * 生成格式：ClassName:MethodName:Param1,Param2...
 */
@Component("customKeyGenerator")
public class CustomKeyGenerator implements KeyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CustomKeyGenerator.class);

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder sb = new StringBuilder();
        // 使用 AopUtils 获取原始类名，避免 CGLIB 代理类名导致 Key 不一致
        Class<?> targetClass = AopUtils.getTargetClass(target);
        sb.append(targetClass.getSimpleName());
        sb.append(":");
        // 方法名
        sb.append(method.getName());
        
        if (params.length > 0) {
            sb.append(":");
            // 参数列表，使用逗号分隔
            String paramsStr = StringUtils.arrayToCommaDelimitedString(params);
            sb.append(paramsStr);
        }
        
        String key = sb.toString();
        logger.debug("Generated key: {} for target class: {}", key, targetClass.getName());
        return key;
    }
}
