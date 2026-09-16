package com.aquarius.feature.sixb6t;

/**
 * Which leg of 6b6t's login relay the proxy is currently on. Every backend switch on 6b6t's
 * proxy network (auth lobby -> main lobby -> real world) resends a full join packet
 * (Bungee/Velocity-style server switch) rather than closing the connection, so none of these
 * besides {@link #REAL_WORLD} should ever be treated as "actually online".
 */
public enum SixB6TPhase {
    UNKNOWN,
    AUTH_LOBBY,
    MAIN_LOBBY,
    PICO_LIMBO,
    REAL_WORLD
}
