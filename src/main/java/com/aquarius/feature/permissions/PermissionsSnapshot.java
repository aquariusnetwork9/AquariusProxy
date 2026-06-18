package com.aquarius.feature.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A structured, read-only snapshot of the RBAC config for the read API ({@code perms export}) — the clean JSON the
 * ProxyBridge mod GUI consumes instead of scraping human-formatted command output. Serializes 1:1 via Gson (record
 * component names are the JSON keys).
 *
 * <p><b>Security:</b> exposes only the token <i>count</i> per user, never the token hashes — the snapshot is safe to
 * hand to any admin-authorized API caller without leaking credential material.
 */
public record PermissionsSnapshot(
    boolean enabled,
    Api api,
    String defaultRole,
    String minConnectRole,
    List<String> roles,
    List<String> groups,
    List<User> users
) {
    public record Api(boolean enabled, String bindHost, int port, int requestsPerMinutePerToken) {}

    public record User(String uuid, String name, String role, List<String> grants, List<String> denies,
                       int tokens, String connectMode, String pearlScope) {}

    public static PermissionsSnapshot of(final PermissionsConfig cfg) {
        final List<User> users = new ArrayList<>();
        for (final Map.Entry<UUID, UserAssignment> e : cfg.users.entrySet()) {
            final UserAssignment ua = e.getValue();
            users.add(new User(
                e.getKey().toString(),
                ua.name,
                ua.role,
                new ArrayList<>(ua.grants),
                new ArrayList<>(ua.denies),
                ua.tokens.size(),                 // count only — never the hashes
                ua.connectMode,
                ua.pearlScope));
        }
        return new PermissionsSnapshot(
            cfg.enabled,
            new Api(cfg.api.enabled, cfg.api.bindHost, cfg.api.port, cfg.api.requestsPerMinutePerToken),
            cfg.defaultRole,
            cfg.minConnectRole,
            new ArrayList<>(cfg.roles.keySet()),
            new ArrayList<>(cfg.groups.keySet()),
            users);
    }
}
