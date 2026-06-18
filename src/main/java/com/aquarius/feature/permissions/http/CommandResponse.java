package com.aquarius.feature.permissions.http;

import java.util.List;

/** Response of {@code POST /command}. Matches the shape the ProxyBridge mod's WebAPI client expects. */
public record CommandResponse(
    String embed,
    String embedComponent,
    List<String> multiLineOutput
) {}
