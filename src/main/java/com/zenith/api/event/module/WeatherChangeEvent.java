package com.zenith.api.event.module;

public record WeatherChangeEvent() {
    public static final WeatherChangeEvent INSTANCE = new WeatherChangeEvent();
}
