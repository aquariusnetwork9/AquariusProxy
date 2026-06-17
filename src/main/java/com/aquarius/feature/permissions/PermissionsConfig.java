package com.aquarius.feature.permissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Config schema for the RBAC system (lives at {@code CONFIG.server.permissions}). Ships <b>disabled</b> so it can
 * land dark with zero behavior change; the resolver ({@link PermissionManager}) is pure and side-effect free.
 *
 * <p>Defaults (groups + roles) are seeded to match {@code docs/RBAC_DESIGN.md}. Everything is user-editable.
 */
public class PermissionsConfig {
    /** Master switch. While false, the proxy keeps its legacy whitelist/owner behavior. */
    public boolean enabled = false;
    /** Role for a subject with no assignment. Locked to "none" (default-deny) by design. */
    public String defaultRole = "none";
    /** A subject must resolve to at least this role to connect at all. */
    public String minConnectRole = "guest";
    /** Capability presets: {@code group.<name>} in a role/grant expands to this list (flat; group refs allowed). */
    public Map<String, List<String>> groups = new LinkedHashMap<>();
    /** Role name -> permission list. May reference {@code group.<name>}. */
    public Map<String, List<String>> roles = new LinkedHashMap<>();
    /** Subject UUID -> assignment. Presence here (with a role) is what grants access; absence = NONE. */
    public Map<UUID, UserAssignment> users = new LinkedHashMap<>();

    public PermissionsConfig() {
        seedDefaultGroups();
        seedDefaultRoles();
    }

    private void seedDefaultGroups() {
        groups.put("movement", list("action.move", "action.interact", "command.goto", "command.pathfinder", "module.boat"));
        groups.put("travel", list("module.elytrapilot", "module.elytratrip"));
        groups.put("combat", list("module.killaura", "module.autototem", "module.autoeat", "module.automend",
            "module.autoarmor", "module.autoomen", "module.spook", "module.autorespawn", "module.spawnpatrol", "module.basepatrol"));
        groups.put("crafting", list("module.villagertrader", "module.pearldrop", "module.aquariusminer", "module.aquariussniffer",
            "module.kitmaker", "module.enchanter", "module.stashscanner", "module.orderfiller", "module.regear"));
        groups.put("automation", list("module.antiafk", "module.autofish", "module.autodrop", "module.tasks"));
        groups.put("chat", list("module.autoreply", "module.extrachat", "module.chathistory", "module.click"));
        // operator-default groups
        groups.put("utility", list("module.antikick", "module.antileak", "module.autodisconnect", "module.sessiontimelimit",
            "module.activehours", "module.requeue", "module.queuewarning"));
        groups.put("system", list("module.bridge", "module.autodetectmodule", "module.autoloadmodule", "module.autoreconnect"));
    }

    private void seedDefaultRoles() {
        roles.put("admin", list("*"));
        roles.put("operator", list("connect.control", "command.info", "command.module", "action.*", "pearl.*",
            "group.movement", "group.travel", "group.combat", "group.crafting", "group.automation",
            "group.chat", "group.utility", "group.system"));
        roles.put("user", list("connect.control", "pearl.pull", "action.move", "action.chat"));
        roles.put("guest", list("pearl.pull"));
    }

    private static List<String> list(String... items) {
        return new ArrayList<>(List.of(items));
    }
}
