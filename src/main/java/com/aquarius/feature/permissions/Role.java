package com.aquarius.feature.permissions;

/**
 * The fixed set of built-in roles, ordered by privilege. Config customizes each role's <i>permission list</i>
 * (see {@link PermissionsConfig#roles}), not the set of roles. {@link #NONE} is the implicit role of any
 * subject with no assignment — it grants nothing (default-deny).
 */
public enum Role {
    NONE,
    GUEST,
    USER,
    OPERATOR,
    ADMIN;

    /** True if this role is at least as privileged as {@code other} (by the declared order). */
    public boolean atLeast(Role other) {
        return ordinal() >= other.ordinal();
    }

    /** Case-insensitive parse; unknown / null falls back to {@link #NONE}. */
    public static Role fromString(String s) {
        if (s == null) return NONE;
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(s.trim())) return r;
        }
        return NONE;
    }

    public String configName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
