package com.aquarius.feature.permissions.http;

/** Body of {@code POST /position}: the caller's own live position, attributed to their token's UUID. */
public record PositionRequest(double x, double y, double z, String dimension) {}
