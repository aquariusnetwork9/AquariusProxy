package com.aquarius.event.module;

public record WeatherChangeEvent() {
    public static final WeatherChangeEvent INSTANCE = new WeatherChangeEvent();
}
