package com.zenith.event.db;

public record RedisRestartEvent() {
    public static final RedisRestartEvent INSTANCE = new RedisRestartEvent();
}
