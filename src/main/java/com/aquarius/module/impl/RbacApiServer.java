package com.aquarius.module.impl;

import com.aquarius.feature.permissions.http.CommandHttpServer;
import com.aquarius.module.api.Module;

import static com.aquarius.Globals.CONFIG;

/**
 * Lifecycle owner for the RBAC HTTP command API. Active only when RBAC <i>and</i> the API are enabled — the API runs
 * commands as a token's subject, so it must never be reachable while RBAC gating is off.
 */
public class RbacApiServer extends Module {
    private final CommandHttpServer server = new CommandHttpServer();

    @Override
    public boolean enabledSetting() {
        return CONFIG.server.permissions.enabled && CONFIG.server.permissions.api.enabled;
    }

    @Override
    public void onEnable() {
        final var api = CONFIG.server.permissions.api;
        server.start(api.bindHost, api.port);
    }

    @Override
    public void onDisable() {
        server.stop();
    }
}
