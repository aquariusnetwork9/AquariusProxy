package com.aquarius.feature.permissions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerTest {

    private static final UUID OWNER = UUID.randomUUID();

    private PermissionManager mgr(PermissionsConfig cfg) {
        return new PermissionManager(cfg, () -> OWNER);
    }

    private UserAssignment user(PermissionsConfig cfg, String name, String role) {
        UUID id = UUID.randomUUID();
        UserAssignment ua = new UserAssignment(name, role);
        cfg.users.put(id, ua);
        return ua;
    }

    private UUID uuidOf(PermissionsConfig cfg, UserAssignment ua) {
        return cfg.users.entrySet().stream().filter(e -> e.getValue() == ua).findFirst().orElseThrow().getKey();
    }

    @Test
    void unknownSubjectIsNoneAndDeniedEverything() {
        var cfg = new PermissionsConfig();
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(UUID.randomUUID(), "Stranger");
        assertSame(Role.NONE, s.role());
        assertFalse(s.allows("pearl.pull"));
        assertFalse(s.allows("module.killaura"));
        assertFalse(mgr.canConnect(s));
    }

    @Test
    void ownerIsAlwaysAdminEvenWithEmptyConfig() {
        var cfg = new PermissionsConfig();
        cfg.roles.clear();   // nuke role defs
        cfg.groups.clear();
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(OWNER, "Me");
        assertTrue(s.owner());
        assertSame(Role.ADMIN, s.role());
        assertTrue(s.allows("anything.at.all"));
        assertTrue(mgr.canConnect(s));
    }

    @Test
    void guestGetsPearlPullOnlyAndCannotConnectByDefault() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Guesty", "guest");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Guesty");
        assertSame(Role.GUEST, s.role());
        assertTrue(s.allows("pearl.pull"));
        assertFalse(s.allows("module.killaura"));
        assertFalse(s.allows("connect.spectate")); // guest is pearl/API-only by default
        assertFalse(mgr.canConnect(s));
    }

    @Test
    void capabilityGroupExpandsForGrantedUser() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Fighter", "user");
        ua.grants.add("group.combat");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Fighter");
        assertTrue(s.allows("module.killaura"));     // from group.combat
        assertTrue(s.allows("module.autototem"));
        assertTrue(s.allows("pearl.pull"));          // from the user role
        assertFalse(s.allows("module.aquariusminer")); // crafting not granted
    }

    @Test
    void operatorGetsAllPresetModulesButNotAdminExclusive() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Op", "operator");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Op");
        assertTrue(s.allows("module.killaura"));        // combat
        assertTrue(s.allows("module.aquariusminer"));   // crafting
        assertTrue(s.allows("module.elytrapilot"));     // travel
        assertTrue(s.allows("connect.control"));
        // admin-exclusive modules are in no preset, so operator must NOT have them
        assertFalse(s.allows("module.coordobfuscation"));
        assertFalse(s.allows("module.spammer"));
        assertFalse(s.allows("module.visualrange"));
        assertFalse(s.allows("command.manage")); // operator has command.info/module, not manage
    }

    @Test
    void rolesAreHierarchical() {
        var cfg = new PermissionsConfig();
        var op = user(cfg, "Op", "operator");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, op), "Op");
        assertTrue(s.allows("pearl.pull"));        // inherited from guest
        assertTrue(s.allows("action.move"));       // inherited from user
        assertTrue(s.allows("connect.control"));   // inherited from user (operator no longer lists it directly)
        assertTrue(s.allows("module.killaura"));   // operator's own (group.combat)

        // user inherits guest but not operator
        var u = user(cfg, "U", "user");
        Subject us = mgr.resolve(uuidOf(cfg, u), "U");
        assertTrue(us.allows("pearl.pull"));        // inherited from guest
        assertTrue(us.allows("connect.control"));   // user's own
        assertFalse(us.allows("module.killaura"));  // operator-level, not inherited downward
        assertFalse(us.allows("command.info"));
    }

    @Test
    void presetGroupsUseRealCommandNames() {
        // The module gate checks "module.<commandName lowercased>", so groups must use real command names.
        var cfg = new PermissionsConfig();
        var u = user(cfg, "Crafter", "user");
        u.grants.add("group.crafting");
        u.grants.add("group.movement");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, u), "Crafter");
        assertTrue(s.allows("module.trader"));      // VillagerTrader's command is "trader", not "villagertrader"
        assertTrue(s.allows("module.pathfinder"));  // baritone goto is the "pathfinder" command
        assertTrue(s.allows("module.pearldrop"));
        assertFalse(s.allows("module.killaura"));   // combat not granted
    }

    @Test
    void adminWildcardImpliesEverything() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Boss", "admin");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Boss");
        assertTrue(s.allows("module.coordobfuscation"));
        assertTrue(s.allows("command.manage"));
        assertTrue(s.allows("perms.whatever"));
    }

    @Test
    void denyBeatsGrant() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Op", "operator");
        ua.denies.add("module.killaura");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Op");
        assertTrue(s.allows("module.autototem"));   // still granted
        assertFalse(s.allows("module.killaura"));    // explicitly denied
    }

    @Test
    void wildcardDenyRemovesAWholeNamespace() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Boss", "admin");
        ua.denies.add("module.*");
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Boss");
        assertFalse(s.allows("module.killaura"));    // denied namespace
        assertTrue(s.allows("command.manage"));      // still has the rest of *
    }

    @Test
    void connectGatingHonoursMinConnectRoleAndConnectPerm() {
        var cfg = new PermissionsConfig();
        var u = user(cfg, "User", "user");
        var mgr = mgr(cfg);
        assertTrue(mgr.canConnect(mgr.resolve(uuidOf(cfg, u), "User"))); // user has connect.control

        // a custom spectate-only role
        cfg.roles.put("viewer", List.of("connect.spectate", "pearl.pull"));
        var v = user(cfg, "Viewer", "viewer");
        // viewer maps to Role.NONE (not a built-in), so it can't meet minConnectRole=guest -> denied
        assertFalse(mgr.canConnect(mgr.resolve(uuidOf(cfg, v), "Viewer")));
    }

    @Test
    void tokenResolvesToItsUserAndUnknownTokenIsNull() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "ApiUser", "user");
        String plaintext = "s3cr3t-token";
        ua.tokens.add(PermissionManager.sha256Hex(plaintext));
        var mgr = mgr(cfg);

        Subject s = mgr.resolveToken(plaintext);
        assertEquals("ApiUser", s.name());
        assertSame(Role.USER, s.role());
        assertTrue(s.allows("pearl.pull"));

        assertNull(mgr.resolveToken("wrong-token"));
        assertNull(mgr.resolveToken(""));
        assertNull(mgr.resolveToken(null));
    }

    @Test
    void trustedSubjectAllowsEverythingAndNoneAllowsNothing() {
        // console / Discord / internal sources resolve to a trusted subject
        Subject trusted = Subject.allPermissions("console");
        assertTrue(trusted.allows("command.manage"));
        assertTrue(trusted.allows("module.coordobfuscation"));
        assertTrue(trusted.allows("anything.at.all"));
        // a player command with no resolvable identity
        Subject none = Subject.none("ghost");
        assertFalse(none.allows("pearl.pull"));
        assertFalse(none.allows("command.info"));
    }

    @Test
    void connectModePreferenceIsCarried() {
        var cfg = new PermissionsConfig();
        var ua = user(cfg, "Spec", "operator");
        ua.connectMode = "spectate";
        var mgr = mgr(cfg);
        Subject s = mgr.resolve(uuidOf(cfg, ua), "Spec");
        assertTrue(s.wantsSpectate());
    }
}
