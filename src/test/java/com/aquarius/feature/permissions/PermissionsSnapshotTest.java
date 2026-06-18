package com.aquarius.feature.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionsSnapshotTest {

    @Test
    void capturesConfigShape() {
        var cfg = new PermissionsConfig();
        cfg.enabled = true;
        UUID id = UUID.randomUUID();
        var ua = new UserAssignment("Steve", "operator");
        ua.grants.add("group.combat");
        cfg.users.put(id, ua);

        var snap = PermissionsSnapshot.of(cfg);

        assertTrue(snap.enabled());
        assertEquals("guest", snap.minConnectRole());
        assertTrue(snap.roles().contains("operator"));
        assertTrue(snap.groups().contains("combat"));
        assertEquals(1, snap.users().size());
        var u = snap.users().get(0);
        assertEquals("Steve", u.name());
        assertEquals("operator", u.role());
        assertEquals(id.toString(), u.uuid());
        assertTrue(u.grants().contains("group.combat"));
    }

    @Test
    void exposesTokenCountNeverHashes() {
        var cfg = new PermissionsConfig();
        var ua = new UserAssignment("Steve", "user");
        String secretHash = PermissionManager.sha256Hex("super-secret-token");
        ua.tokens.add(secretHash);
        ua.tokens.add(PermissionManager.sha256Hex("another"));
        cfg.users.put(UUID.randomUUID(), ua);

        var snap = PermissionsSnapshot.of(cfg);
        assertEquals(2, snap.users().get(0).tokens());

        // the serialized snapshot must not contain any token hash material
        String json = new Gson().toJson(snap);
        assertFalse(json.contains(secretHash), "token hash leaked into export JSON");
        assertFalse(json.toLowerCase().contains("token") && json.contains(secretHash));
    }

    @Test
    void emptyConfigSerializesCleanly() {
        var snap = PermissionsSnapshot.of(new PermissionsConfig());
        String json = new Gson().toJson(snap);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"users\":[]"));
        assertFalse(snap.enabled());
    }
}
