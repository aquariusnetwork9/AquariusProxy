package com.aquarius.feature.permissions;

import java.util.ArrayList;
import java.util.List;

/**
 * A single subject's assignment in {@link PermissionsConfig#users} (keyed by UUID). Mutable POJO so it
 * serializes cleanly via the existing gson config.
 */
public class UserAssignment {
    /** Display name (refreshed from the UUID); informational only. */
    public String name = "";
    /** Role name (see {@link Role}); unknown falls back to NONE. */
    public String role = "guest";
    /** Per-user additions on top of the role — may reference {@code group.<name>}. */
    public List<String> grants = new ArrayList<>();
    /** Per-user removals; a deny always beats a grant. */
    public List<String> denies = new ArrayList<>();
    /** SHA-256 hex of each issued API token (plaintext is shown once at issue time, never stored). */
    public List<String> tokens = new ArrayList<>();
    /** Which pearls this subject may pull: {@code self} (only their own) or {@code none}. */
    public String pearlScope = "self";
    /** How this subject joins: {@code control} or {@code spectate} (see the Join-as-spectator feature). */
    public String connectMode = "control";

    public UserAssignment() {}

    public UserAssignment(String name, String role) {
        this.name = name;
        this.role = role;
    }
}
