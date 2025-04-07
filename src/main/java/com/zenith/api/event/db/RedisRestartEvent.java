package com.zenith.api.event.db;

public record RedisRestartEvent() {
    public static final RedisRestartEvent INSTANCE = new RedisRestartEvent();
}
