package com.cache.springboot3cache.config;

import java.io.Serializable;

/**
 * 缓存值包装类
 * 用于存储缓存值及其创建时间，以便实现逻辑过期和自动刷新
 */
public class RefreshWrapper implements Serializable {
    // 实际缓存值
    private Object value;
    // 缓存创建时间（毫秒）
    private long createTime;

    /**
     * 默认构造函数，用于反序列化
     */
    public RefreshWrapper() {
    }

    /**
     * 构造函数
     *
     * @param value 缓存值
     * @param createTime 创建时间
     */
    public RefreshWrapper(Object value, long createTime) {
        this.value = value;
        this.createTime = createTime;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
