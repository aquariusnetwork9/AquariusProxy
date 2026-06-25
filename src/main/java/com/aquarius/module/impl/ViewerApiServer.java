package com.aquarius.module.impl;

import com.aquarius.feature.viewer.ViewerHttpServer;
import com.aquarius.module.api.Module;

import static com.aquarius.Globals.CONFIG;

/**
 * Lifecycle owner for the read-only viewer HTTP feed that backs the Aquarius Bot Manager's live map / POV viewer.
 * Loopback-bound and unauthenticated by design (the manager relays it over its own authenticated tunnel), so it runs
 * only when explicitly enabled.
 */
public class ViewerApiServer extends Module {
    private final ViewerHttpServer server = new ViewerHttpServer();

    @Override
    public boolean enabledSetting() {
        return CONFIG.server.viewer.enabled;
    }

    @Override
    public void onEnable() {
        server.start(CONFIG.server.viewer.bindHost, CONFIG.server.viewer.port);
    }

    @Override
    public void onDisable() {
        server.stop();
    }

    /** Re-bind to the current host/port after changing them at runtime; no-op while not running. */
    public void rebind() {
        if (!isEnabled()) return;
        server.stop();
        server.start(CONFIG.server.viewer.bindHost, CONFIG.server.viewer.port);
    }
}
