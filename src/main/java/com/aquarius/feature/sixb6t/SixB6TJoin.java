package com.aquarius.feature.sixb6t;

import com.aquarius.event.sixb6t.SixB6TPhaseChangedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static com.aquarius.Globals.CLIENT_LOG;
import static com.aquarius.Globals.EVENT_BUS;

/**
 * Classifies which leg of 6b6t's login relay the proxy is on, and tracks the PicoLimbo backup
 * -server holding pattern. Pure state + classification, mirroring {@link com.aquarius.feature.queue.Queue}'s
 * shape -- {@link com.aquarius.module.impl.SixB6TAutoLogin} owns the actual reactions (sending
 * "/login", walking through the portal, giving up and disconnecting after too long in limbo).
 *
 * Classification is dimension-count based, from a live packet capture of a cracked-account login
 * (2026-08): the auth lobby declares 2 dimensions ({@code [the_end, overworld]}, gamemode
 * ADVENTURE), the intermediate main lobby declares 1 ({@code [overworld]}, ADVENTURE), and the
 * real survival world declares all 3. PicoLimbo isn't distinguishable by dimension count -- it
 * only reveals itself in chat -- so it's caught separately via {@link #onSystemChat}.
 */
public final class SixB6TJoin {
    private static final List<String> PICO_LIMBO_MARKERS = List.of(
        "backup server", "while 6b6t is starting", "while the server is starting"
    );

    private static volatile SixB6TPhase phase = SixB6TPhase.UNKNOWN;
    private static volatile Instant limboSince = null;

    private SixB6TJoin() {}

    public static SixB6TPhase getPhase() {
        return phase;
    }

    public static Instant getLimboSince() {
        return limboSince;
    }

    public static boolean isReadyForRealWorld() {
        return phase == SixB6TPhase.REAL_WORLD;
    }

    public static void reset() {
        setPhase(SixB6TPhase.UNKNOWN);
    }

    public static void onLogin(int dimensionCount) {
        setPhase(classify(dimensionCount));
    }

    public static void onSystemChat(String messageString) {
        var lower = messageString.toLowerCase(Locale.ROOT);
        if (PICO_LIMBO_MARKERS.stream().anyMatch(lower::contains)) {
            setPhase(SixB6TPhase.PICO_LIMBO);
        }
    }

    private static SixB6TPhase classify(int dimensionCount) {
        return switch (dimensionCount) {
            case 3 -> SixB6TPhase.REAL_WORLD;
            case 2 -> SixB6TPhase.AUTH_LOBBY;
            case 1 -> SixB6TPhase.MAIN_LOBBY;
            default -> SixB6TPhase.UNKNOWN;
        };
    }

    private static synchronized void setPhase(SixB6TPhase next) {
        var prev = phase;
        if (prev == next) return;
        phase = next;
        limboSince = next == SixB6TPhase.PICO_LIMBO ? Instant.now() : null;
        CLIENT_LOG.debug("6b6t join phase: {} -> {}", prev, next);
        EVENT_BUS.postAsync(new SixB6TPhaseChangedEvent(prev, next));
    }
}
