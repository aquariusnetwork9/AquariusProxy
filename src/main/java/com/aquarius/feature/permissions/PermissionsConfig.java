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

    /** Token-authorized HTTP command API. Off + localhost-only by default. */
    public final ApiConfig api = new ApiConfig();

    public static class ApiConfig {
        public boolean enabled = false;
        /** Bind address — localhost-only by default; set to 0.0.0.0 to expose (do so deliberately). */
        public String bindHost = "127.0.0.1";
        public int port = 2480;
        public int requestsPerMinutePerToken = 60;
    }

    public PermissionsConfig() {
        seedDefaultGroups();
        seedDefaultRoles();
    }

    private void seedDefaultGroups() {
        // module.<name> uses the command's name (lowercased); baritone "goto" is the pathfinder command.
        groups.put("movement", list("action.move", "action.interact", "module.pathfinder", "module.boat"));
        groups.put("travel", list("module.elytrapilot")); // ElytraTrip has no separate command; elytrapilot covers it
        groups.put("combat", list("module.killaura", "module.autototem", "module.autoeat", "module.automend",
            "module.autoarmor", "module.autoomen", "module.spook", "module.autorespawn", "module.spawnpatrol", "module.basepatrol",
            "module.whispercontrol")); // whisper-command handler; verbs also need their own perm (pathfinder/aquariusminer/etc.)
        groups.put("crafting", list("module.trader", "module.pearldrop", "module.aquariusminer",
            "module.kitmaker", "module.enchanter", "module.stashscanner", "module.orderfiller", "module.regear"));
        groups.put("automation", list("module.antiafk", "module.autofish", "module.autodrop", "module.tasks"));
        groups.put("chat", list("module.autoreply", "module.extrachat", "module.chathistory", "module.click"));
        // operator-default groups
        groups.put("utility", list("module.antikick", "module.antileak", "module.autodisconnect", "module.sessiontimelimit",
            "module.activehours", "module.requeue", "module.queuewarning"));
        groups.put("system", list("module.bridge", "module.autodetectmodule", "module.autoloadmodule", "module.autoreconnect"));
    }

    private void seedDefaultRoles() {
        // Roles are hierarchical: each lists only what it ADDS over the role below it (admin ⊇ operator ⊇ user ⊇ guest).
        roles.put("guest", list("pearl.pull"));
        roles.put("user", list("connect.control", "action.move", "action.chat"));            // + inherits guest
        roles.put("operator", list("command.info", "command.module", "action.*", "pearl.*",  // + inherits user/guest
            "group.movement", "group.travel", "group.combat", "group.crafting", "group.automation",
            "group.chat", "group.utility", "group.system"));
        roles.put("admin", list("*"));
    }

    private static List<String> list(String... items) {
        return new ArrayList<>(List.of(items));
    }
}
