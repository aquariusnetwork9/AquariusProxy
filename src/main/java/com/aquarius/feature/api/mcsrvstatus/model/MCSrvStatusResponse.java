package com.aquarius.feature.api.mcsrvstatus.model;

public record MCSrvStatusResponse(
    boolean online,
    String ip,
    int port
) {
}
