package com.aquarius.feature.viewer;

/**
 * Local diagnostic viewer API — a read-only HTTP feed of the bot's live state (position, look, health, …) that the
 * Aquarius Bot Manager polls to render a live map / POV viewer in its dashboard. Bound to loopback by default and
 * carries no auth, so it must never be exposed beyond the box; the manager relays it over its own authenticated
 * tunnel. Ships DISABLED — enable per bot when you want the dashboard viewer.
 */
public final class ViewerConfig {
    /** Master switch. Off = no listener at all. */
    public boolean enabled = false;

    /** Bind address. Keep on loopback — this endpoint is unauthenticated by design. */
    public String bindHost = "127.0.0.1";

    /** Listen port for the viewer HTTP feed. */
    public int port = 2998;

    /**
     * Allow the dashboard to CONTROL the bot through this feed (run commands), not just observe it. Opt-in: when
     * off, {@code /control/command} is refused (the read-only {@code /control/state} + {@code /control/commands}
     * metadata stay available). Same trust model as the rest of the feed — loopback only, relayed by the manager
     * over its authenticated tunnel.
     */
    public boolean control = false;
}
