package com.aquarius.feature.permissions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pure resolver for the RBAC system: given a {@link PermissionsConfig} and a subject identifier (UUID or API
 * token), produces a {@link Subject} with its fully-expanded effective permissions, and answers
 * {@link #allows}. No side effects — safe to construct anywhere and unit-test in isolation.
 *
 * <p>Resolution: {@code effectiveGranted = expand(role.perms ∪ user.grants)},
 * {@code effectiveDenied = expand(user.denies)}; {@code allows = grantedImplies(p) && !deniedImplies(p)}.
 * Group references ({@code group.<name>}) are expanded recursively with a cycle guard. The proxy's own MC
 * account always resolves to {@link Role#ADMIN} (never lockable-out, even if the config is empty/broken).
 */
public class PermissionManager {

    private final PermissionsConfig config;
    private final Supplier<UUID> ownerUuidSupplier;

    public PermissionManager(PermissionsConfig config, Supplier<UUID> ownerUuidSupplier) {
        this.config = config;
        this.ownerUuidSupplier = ownerUuidSupplier != null ? ownerUuidSupplier : () -> null;
    }

    /** Resolve a connected player by UUID. Unknown UUID => the configured {@code defaultRole} (NONE by default). */
    public Subject resolve(UUID uuid, String name) {
        if (isOwner(uuid)) return ownerSubject(uuid, name);
        UserAssignment ua = uuid != null ? config.users.get(uuid) : null;
        Role role = ua != null ? Role.fromString(ua.role) : Role.fromString(config.defaultRole);

        List<String> grantInputs = new ArrayList<>(rolePermissions(role));
        if (ua != null) grantInputs.addAll(ua.grants);
        Set<String> granted = expand(grantInputs);
        Set<String> denied = ua != null ? expand(ua.denies) : Set.of();

        String name0 = name != null ? name : (ua != null ? ua.name : "");
        return new Subject(uuid, name0, role, granted, denied,
            ua != null ? ua.pearlScope : "none",
            ua != null ? ua.connectMode : "control",
            false);
    }

    /** Resolve an API caller by plaintext token. Unknown/blank token => {@code null} (no access). */
    public Subject resolveToken(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) return null;
        String hash = sha256Hex(plaintextToken);
        for (var entry : config.users.entrySet()) {
            for (String t : entry.getValue().tokens) {
                if (constantTimeEquals(t, hash)) {
                    return resolve(entry.getKey(), entry.getValue().name);
                }
            }
        }
        return null;
    }

    public boolean allows(Subject subject, String permission) {
        return subject != null && subject.allows(permission);
    }

    public boolean canConnect(Subject subject) {
        if (subject == null) return false;
        if (subject.owner()) return true;
        Role min = Role.fromString(config.minConnectRole);
        if (!subject.role().atLeast(min)) return false;
        return subject.allows("connect.control") || subject.allows("connect.spectate");
    }

    public boolean isOwner(UUID uuid) {
        UUID owner = ownerUuidSupplier.get();
        return uuid != null && uuid.equals(owner);
    }

    private Subject ownerSubject(UUID uuid, String name) {
        return new Subject(uuid, name != null ? name : "owner", Role.ADMIN, Set.of("*"), Set.of(), "self", "control", true);
    }

    private List<String> rolePermissions(Role role) {
        return config.roles.getOrDefault(role.configName(), List.of());
    }

    /** Expand a permission list: pass through plain perms, recursively inline {@code group.<name>} bundles. */
    Set<String> expand(Collection<String> perms) {
        Set<String> out = new HashSet<>();
        Set<String> visitedGroups = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(perms);
        while (!stack.isEmpty()) {
            String p = stack.pop();
            if (p == null || p.isBlank()) continue;
            if (p.startsWith("group.")) {
                String g = p.substring("group.".length());
                if (visitedGroups.add(g)) {
                    stack.addAll(config.groups.getOrDefault(g, List.of()));
                }
            } else {
                out.add(p);
            }
        }
        return out;
    }

    static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
