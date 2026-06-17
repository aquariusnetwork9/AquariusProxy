package com.aquarius.feature.permissions;

import com.aquarius.feature.whitelist.PlayerEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistMigrationTest {

    private static PlayerEntry entry(String name) {
        return new PlayerEntry(name, UUID.randomUUID(), 0L);
    }

    @Test
    void mapsWhitelistToControlAndSpectatorsToGuestSpectate() {
        var cfg = new PermissionsConfig();
        var wl = entry("Steve");
        var spec = entry("Alex");

        var result = WhitelistMigration.run(List.of(wl), List.of(spec), cfg, true);

        assertEquals(1, result.controlAdded());
        assertEquals(1, result.spectateAdded());
        assertEquals(0, result.skipped());

        UserAssignment steve = cfg.users.get(wl.getUuid());
        assertEquals("user", steve.role);
        assertEquals("control", steve.connectMode);
        assertTrue(steve.grants.isEmpty());

        UserAssignment alex = cfg.users.get(spec.getUuid());
        assertEquals("guest", alex.role);
        assertEquals("spectate", alex.connectMode);
        assertTrue(alex.grants.contains("connect.spectate"));
    }

    @Test
    void dryRunDoesNotMutateButCountsMatchApply() {
        var cfg = new PermissionsConfig();
        var wl = List.of(entry("A"), entry("B"));
        var spec = List.of(entry("C"));

        var preview = WhitelistMigration.run(wl, spec, cfg, false);
        assertTrue(cfg.users.isEmpty(), "dry run must not write");
        assertEquals(2, preview.controlAdded());
        assertEquals(1, preview.spectateAdded());

        var applied = WhitelistMigration.run(wl, spec, cfg, true);
        assertEquals(preview.controlAdded(), applied.controlAdded());
        assertEquals(preview.spectateAdded(), applied.spectateAdded());
        assertEquals(3, cfg.users.size());
    }

    @Test
    void alreadyAssignedSubjectsAreSkipped() {
        var cfg = new PermissionsConfig();
        var wl = entry("Steve");
        cfg.users.put(wl.getUuid(), new UserAssignment("Steve", "admin"));   // pre-existing, must not be downgraded

        var result = WhitelistMigration.run(List.of(wl), List.of(), cfg, true);

        assertEquals(0, result.controlAdded());
        assertEquals(1, result.skipped());
        assertEquals("admin", cfg.users.get(wl.getUuid()).role, "existing assignment preserved");
    }

    @Test
    void uuidInBothListsIsAssignedOnceWithControlWinning() {
        var cfg = new PermissionsConfig();
        var both = entry("Steve");

        var result = WhitelistMigration.run(List.of(both), List.of(both), cfg, true);

        assertEquals(1, result.controlAdded());
        assertEquals(0, result.spectateAdded());
        assertEquals(1, result.skipped());   // the spectator pass skips the already-planned UUID
        assertEquals(1, cfg.users.size());
        UserAssignment ua = cfg.users.get(both.getUuid());
        assertEquals("user", ua.role);
        assertEquals("control", ua.connectMode);
    }

    @Test
    void reRunIsIdempotent() {
        var cfg = new PermissionsConfig();
        var wl = List.of(entry("A"), entry("B"));
        var spec = List.of(entry("C"));

        WhitelistMigration.run(wl, spec, cfg, true);
        var second = WhitelistMigration.run(wl, spec, cfg, true);

        assertEquals(0, second.controlAdded());
        assertEquals(0, second.spectateAdded());
        assertEquals(3, second.skipped());
        assertEquals(3, cfg.users.size());
    }

    @Test
    void emptyListsImportNothing() {
        var cfg = new PermissionsConfig();
        var result = WhitelistMigration.run(List.of(), List.of(), cfg, true);
        assertEquals(0, result.total());
        assertNull(cfg.users.get(UUID.randomUUID()));
        assertFalse(result.total() > 0);
    }
}
