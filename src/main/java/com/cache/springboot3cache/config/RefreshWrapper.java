package com.cache.springboot3cache.config;

import java.io.Serializable;

public class RefreshWrapper implements Serializable {
    private Object value;
    private long createTime;

    public RefreshWrapper() {
    }

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
