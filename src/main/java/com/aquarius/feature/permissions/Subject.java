package com.aquarius.feature.permissions;

import java.util.Set;
import java.util.UUID;

/**
 * A resolved identity and its effective permissions. {@code granted}/{@code denied} are already
 * fully expanded (groups resolved). Permission checks are wildcard-aware and deny-wins.
 *
 * @param owner true for the proxy's own MC account — always allowed (fail-safe, never lockable-out).
 */
public record Subject(
    UUID uuid,
    String name,
    Role role,
    Set<String> granted,
    Set<String> denied,
    String pearlScope,
    String connectMode,
    boolean owner
) {
    /** A fully-trusted subject (everything allowed) — for the console, Discord, and internal automation sources. */
    public static Subject allPermissions(String name) {
        return new Subject(null, name, Role.ADMIN, Set.of("*"), Set.of(), "self", "control", true);
    }

    /** A no-access subject (NONE) — e.g. a player command with no resolvable identity. */
    public static Subject none(String name) {
        return new Subject(null, name, Role.NONE, Set.of(), Set.of(), "none", "control", false);
    }

    /** Does this subject hold {@code permission}? Owner always yes; otherwise a grant must imply it and no deny may. */
    public boolean allows(String permission) {
        if (owner) return true;
        if (!impliedBy(granted, permission)) return false;
        return !impliedBy(denied, permission);
    }

    public boolean wantsSpectate() {
        return "spectate".equalsIgnoreCase(connectMode);
    }

    /** True if any held permission string implies {@code required} (supports {@code *} and trailing {@code .*}). */
    static boolean impliedBy(Set<String> held, String required) {
        if (held.contains(required) || held.contains("*")) return true;
        // walk wildcard parents: a.b.c is implied by a.*, a.b.* (and *)
        for (String h : held) {
            if (implies(h, required)) return true;
        }
        return false;
    }

    static boolean implies(String granted, String required) {
        if (granted.equals("*") || granted.equals(required)) return true;
        if (granted.endsWith(".*")) {
            String prefix = granted.substring(0, granted.length() - 1); // keep trailing dot: "module."
            return required.startsWith(prefix);
        }
        return false;
    }
}
