package com.aquarius.feature.permissions;

import com.aquarius.feature.whitelist.PlayerEntry;
import com.aquarius.feature.whitelist.PlayerListsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One-time importer from the legacy player lists into RBAC user assignments. Mapping:
 *
 * <ul>
 *   <li><b>whitelist</b> (legacy connect/control access) → role {@code user} (control).</li>
 *   <li><b>spectator whitelist</b>, members not already imported as control → role {@code guest} with an explicit
 *       {@code connect.spectate} grant and {@code connectMode=spectate}, preserving their spectate-only access
 *       under the new default-deny model.</li>
 *   <li><b>blacklist</b> is intentionally ignored — under default-deny, anyone not assigned already has no access.</li>
 * </ul>
 *
 * <p>Already-assigned subjects (present in {@link PermissionsConfig#users}) are left untouched, so the import is
 * idempotent and safe to re-run. A UUID appearing in both lists, or twice, is assigned once (control wins). The
 * dry-run and apply paths walk identical logic (a local planned-set), so the preview counts match what apply writes.
 */
public final class WhitelistMigration {
    private WhitelistMigration() {}

    public record Result(int controlAdded, int spectateAdded, int skipped, List<String> details) {
        public int total() { return controlAdded + spectateAdded; }
    }

    /**
     * @param apply {@code false} = preview only (no mutation); {@code true} = write the assignments into {@code cfg.users}.
     */
    public static Result run(final PlayerListsManager lists, final PermissionsConfig cfg, final boolean apply) {
        return run(lists.getWhitelist().entries(), lists.getSpectatorWhitelist().entries(), cfg, apply);
    }

    /** Core logic over the raw entry lists (split out so it's unit-testable without bootstrapping the config/globals). */
    public static Result run(final List<PlayerEntry> whitelist, final List<PlayerEntry> spectators,
                             final PermissionsConfig cfg, final boolean apply) {
        final Set<UUID> planned = new HashSet<>(cfg.users.keySet());
        final List<String> details = new ArrayList<>();
        int control = 0, spectate = 0, skipped = 0;

        for (final PlayerEntry e : whitelist) {
            if (!planned.add(e.getUuid())) { skipped++; continue; }
            details.add(e.getUsername() + " → user (control)");
            if (apply) cfg.users.put(e.getUuid(), new UserAssignment(e.getUsername(), "user"));
            control++;
        }
        for (final PlayerEntry e : spectators) {
            if (!planned.add(e.getUuid())) { skipped++; continue; }
            details.add(e.getUsername() + " → guest (spectate)");
            if (apply) {
                final UserAssignment ua = new UserAssignment(e.getUsername(), "guest");
                ua.connectMode = "spectate";
                ua.grants.add("connect.spectate");
                cfg.users.put(e.getUuid(), ua);
            }
            spectate++;
        }
        return new Result(control, spectate, skipped, details);
    }
}
