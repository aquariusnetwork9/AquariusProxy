package com.aquarius.discord;

import com.aquarius.feature.permissions.PermissionManager;
import com.aquarius.feature.permissions.PermissionsConfig;
import com.aquarius.feature.permissions.Role;
import com.aquarius.feature.permissions.UserAssignment;
import com.aquarius.feature.whitelist.PlayerListsManager;
import com.aquarius.module.impl.RbacApiServer;
import com.aquarius.module.impl.RbacGuard;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for the RBAC system ({@code CONFIG.server.permissions}) — the simplified, Discord-shaped
 * counterpart to the {@code /perms} command and the (fuller) ProxyBridge mod GUI. Lets an admin, from chat:
 * toggle RBAC + the HTTP API, add/bulk-assign users (modal), pick a user from a dropdown and set their role, toggle
 * capability presets ({@code group.*}) on/off like checkboxes, issue (shown once, ephemerally) / revoke API tokens,
 * flip their connect mode (control ⇄ spectate), and remove them.
 *
 * <p>All controls mutate the config directly (the config IS the panel state); the only extra state is the in-memory
 * {@link #selected} user being edited, kept on this singleton (ephemeral UI state, not persisted). Owner-gated and
 * prefix-routed by {@link DiscordPanel}. Posted with {@code /perms panel}.
 */
public final class PermsPanel extends DiscordPanel {

    private static final String PREFIX = "perms:";
    private static final String ENABLE = "perms:enable", API = "perms:api", RELOAD = "perms:reload",
        ADD = "perms:add", ISSUE = "perms:issue", REVOKE = "perms:revoke", MODE = "perms:mode",
        REMOVE = "perms:remove", CLEAR = "perms:clear",
        USERSEL = "perms:usersel", ROLESEL = "perms:rolesel", PRESETSEL = "perms:presetsel",
        APICFG = "perms:apicfg",
        ADDMODAL = "perms:addmodal", APIMODAL = "perms:apimodal";

    private static final String[] ROLE_NAMES = {"guest", "user", "operator", "admin"};
    private static final SecureRandom RANDOM = new SecureRandom();

    /** UUID of the user currently being edited in the panel (in-memory UI state only). */
    private UUID selected;

    @Override protected String prefix() { return PREFIX; }

    private static PermissionsConfig cfg() { return CONFIG.server.permissions; }

    private UserAssignment selectedUser() {
        return selected == null ? null : cfg().users.get(selected);
    }

    private static void resync() {
        MODULE.get(RbacGuard.class).syncEnabledFromConfig();
        MODULE.get(RbacApiServer.class).syncEnabledFromConfig();
    }

    // ---------------------------------------------------------------- render

    @Override
    protected Embed embed() {
        var cfg = cfg();
        Embed e = new Embed().primaryColor().title("Access Control (RBAC)")
            .addField("Status", cfg.enabled ? "🔐 ENABLED (replaces whitelist)" : "🔓 disabled (legacy whitelist)", false)
            .addField("HTTP API", (cfg.api.enabled ? "on" : "off") + "  ·  " + cfg.api.bindHost + ":" + cfg.api.port, true)
            .addField("Users", String.valueOf(cfg.users.size()), true)
            .addField("Min connect role", cfg.minConnectRole, true);
        UserAssignment ua = selectedUser();
        if (ua == null) {
            e.description("Pick a user from the dropdown to manage, or **➕ Add users**. "
                + "Default-deny: anyone not listed gets no connection and no interaction.");
        } else {
            e.addField("▶ Editing", ua.name + "  (" + ua.role + ")", false)
             .addField("Grants", ua.grants.isEmpty() ? "(role only)" : String.join(", ", ua.grants), false)
             .addField("Denies", ua.denies.isEmpty() ? "(none)" : String.join(", ", ua.denies), false)
             .addField("Tokens", String.valueOf(ua.tokens.size()), true)
             .addField("Mode", ua.connectMode, true)
             .addField("Pearl scope", ua.pearlScope, true);
        }
        return e;
    }

    @Override
    protected List<ActionRow> components() {
        var cfg = cfg();
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            cfg.enabled ? Button.success(ENABLE, "RBAC: ON") : Button.secondary(ENABLE, "RBAC: off"),
            cfg.api.enabled ? Button.success(API, "API: ON") : Button.secondary(API, "API: off"),
            Button.secondary(APICFG, "⚙ API addr"),
            Button.secondary(RELOAD, "🔄 Reload"),
            Button.primary(ADD, "➕ Add users")
        ));
        if (!cfg.users.isEmpty()) {
            var sel = StringSelectMenu.create(USERSEL).setPlaceholder("Manage user…");
            int n = 0;
            for (var entry : cfg.users.entrySet()) {
                if (++n > 25) break;
                UserAssignment ua = entry.getValue();
                boolean isSel = entry.getKey().equals(selected);
                sel.addOption(ua.name, entry.getKey().toString(), ua.role + (isSel ? "  ✓ editing" : ""));
            }
            rows.add(ActionRow.of(sel.build()));
        }
        UserAssignment ua = selectedUser();
        if (ua != null) {
            var roleSel = StringSelectMenu.create(ROLESEL).setPlaceholder("Role: " + ua.role);
            for (String r : ROLE_NAMES) {
                if (r.equals(ua.role)) roleSel.addOption(r, r, "current");
                else roleSel.addOption(r, r);
            }
            rows.add(ActionRow.of(roleSel.build()));

            var presetSel = StringSelectMenu.create(PRESETSEL).setPlaceholder("Toggle capability preset…");
            for (String g : cfg.groups.keySet()) {
                boolean granted = ua.grants.contains("group." + g);
                presetSel.addOption((granted ? "✓ " : "") + g, g, granted ? "user-granted — tap to remove" : "tap to grant");
            }
            rows.add(ActionRow.of(presetSel.build()));

            rows.add(ActionRow.of(
                Button.secondary(ISSUE, "🎫 Issue token"),
                Button.secondary(REVOKE, "♻ Revoke tokens (" + ua.tokens.size() + ")"),
                Button.secondary(MODE, "Mode: " + ua.connectMode),
                Button.danger(REMOVE, "🗑 Remove"),
                Button.secondary(CLEAR, "✖ Clear")
            ));
        }
        return rows;
    }

    // ---------------------------------------------------------------- modal

    private Modal addModal() {
        TextInput names = TextInput.create("perms:names", TextInputStyle.PARAGRAPH).setRequired(true)
            .setPlaceholder("usernames, comma- or newline-separated (bulk-assign supported)").build();
        TextInput role = TextInput.create("perms:role", TextInputStyle.SHORT).setRequired(true)
            .setValue("user").setPlaceholder("guest / user / operator / admin").build();
        return Modal.create(ADDMODAL, "Add / assign users")
            .addComponents(Label.of("Usernames", names), Label.of("Role", role))
            .build();
    }

    private Modal apiModal() {
        var api = cfg().api;
        TextInput host = TextInput.create("perms:apihost", TextInputStyle.SHORT).setRequired(true)
            .setValue(api.bindHost).setPlaceholder("127.0.0.1 (localhost) or 0.0.0.0 to expose").build();
        TextInput port = TextInput.create("perms:apiport", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(api.port)).setPlaceholder("1-65535").build();
        TextInput rpm = TextInput.create("perms:apirpm", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(api.requestsPerMinutePerToken)).setPlaceholder("requests/min per token (0 = unlimited)").build();
        return Modal.create(APIMODAL, "HTTP API address")
            .addComponents(Label.of("Bind host", host), Label.of("Port", port), Label.of("Rate limit / token", rpm))
            .build();
    }

    // ---------------------------------------------------------------- interaction handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var cfg = cfg();
        switch (e.getComponentId()) {
            case ENABLE -> {
                cfg.enabled = !cfg.enabled;
                resync();
                e.getChannel().sendMessage(cfg.enabled
                    ? "🔐 RBAC **enabled** — now replacing the whitelist. Unassigned players have no access."
                    : "🔓 RBAC **disabled** — reverted to the legacy whitelist/owner behavior.").queue();
            }
            case API -> {
                cfg.api.enabled = !cfg.api.enabled;
                MODULE.get(RbacApiServer.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("HTTP command API " + (cfg.api.enabled
                    ? "**on** (" + cfg.api.bindHost + ":" + cfg.api.port + ")" : "**off**")).queue();
            }
            case RELOAD -> {
                resync();
                e.getChannel().sendMessage("RBAC modules re-synced from config.").queue();
            }
            case ADD -> { e.replyModal(addModal()).queue(); return false; }
            case APICFG -> { e.replyModal(apiModal()).queue(); return false; }
            case ISSUE -> {
                UserAssignment ua = selectedUser();
                if (ua == null) { e.reply("Select a user first.").setEphemeral(true).queue(); return false; }
                String plaintext = randomToken();
                ua.tokens.add(PermissionManager.sha256Hex(plaintext));
                e.reply("🎫 Token for **" + ua.name + "** (shown once — store it now):\n`" + plaintext + "`")
                    .setEphemeral(true).queue();
                return false;   // ephemeral secret; the token count refreshes on the next render
            }
            case REVOKE -> {
                UserAssignment ua = selectedUser();
                if (ua == null) { e.reply("Select a user first.").setEphemeral(true).queue(); return false; }
                int n = ua.tokens.size();
                ua.tokens.clear();
                e.getChannel().sendMessage("Revoked " + n + " token(s) for " + ua.name + ".").queue();
            }
            case MODE -> {
                UserAssignment ua = selectedUser();
                if (ua == null) { e.reply("Select a user first.").setEphemeral(true).queue(); return false; }
                ua.connectMode = ua.connectMode.equalsIgnoreCase("spectate") ? "control" : "spectate";
            }
            case REMOVE -> {
                UserAssignment ua = selectedUser();
                if (ua == null) { e.reply("Select a user first.").setEphemeral(true).queue(); return false; }
                cfg.users.remove(selected);
                String nm = ua.name;
                selected = null;
                e.getChannel().sendMessage("Removed " + nm + " from access control.").queue();
            }
            case CLEAR -> selected = null;
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        switch (e.getComponentId()) {
            case USERSEL -> {
                try { selected = UUID.fromString(e.getValues().get(0)); }
                catch (Exception ex) { selected = null; }
                return true;
            }
            case ROLESEL -> {
                UserAssignment ua = selectedUser();
                if (ua == null) return false;
                String r = e.getValues().get(0).toLowerCase();
                if (Role.fromString(r) != Role.NONE) ua.role = r;
                return true;
            }
            case PRESETSEL -> {
                UserAssignment ua = selectedUser();
                if (ua == null) return false;
                String perm = "group." + e.getValues().get(0);
                if (!ua.grants.remove(perm)) ua.grants.add(perm);   // toggle: present → remove, absent → add
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (APIMODAL.equals(e.getModalId())) return onApiModal(e);
        if (!ADDMODAL.equals(e.getModalId())) return false;
        String role = e.getValue("perms:role").getAsString().trim().toLowerCase();
        if (Role.fromString(role) == Role.NONE) {
            e.reply("Unknown role '" + role + "' — use guest, user, operator, or admin.").setEphemeral(true).queue();
            return false;
        }
        String[] names = e.getValue("perms:names").getAsString().split("[,\\n]");
        int added = 0;
        List<String> failed = new ArrayList<>();
        UUID last = null;
        for (String nameRaw : names) {
            String name = nameRaw.trim();
            if (name.isEmpty()) continue;
            UserAssignment existing = findByName(name);
            if (existing != null) { existing.role = role; added++; continue; }
            var entry = PlayerListsManager.createPlayerListEntry(name).orElse(null);
            if (entry == null) { failed.add(name); continue; }
            cfg().users.put(entry.getUuid(), new UserAssignment(entry.getUsername(), role));
            last = entry.getUuid();
            added++;
        }
        if (last != null) selected = last;
        StringBuilder msg = new StringBuilder("Assigned " + added + " user(s) as " + role + ".");
        if (!failed.isEmpty()) msg.append(" Could not resolve: ").append(String.join(", ", failed)).append('.');
        e.getChannel().sendMessage(msg.toString()).queue();
        return true;
    }

    private boolean onApiModal(ModalInteractionEvent e) {
        var api = cfg().api;
        String host = e.getValue("perms:apihost").getAsString().trim();
        int port, rpm;
        try {
            port = Integer.parseInt(e.getValue("perms:apiport").getAsString().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            rpm = Math.max(0, Integer.parseInt(e.getValue("perms:apirpm").getAsString().trim()));
        } catch (Exception ex) {
            e.reply("Port must be 1-65535 and the rate limit a whole number ≥ 0.").setEphemeral(true).queue();
            return false;
        }
        if (host.isEmpty()) { e.reply("Bind host is required.").setEphemeral(true).queue(); return false; }
        api.bindHost = host;
        api.port = port;
        api.requestsPerMinutePerToken = rpm;
        MODULE.get(RbacApiServer.class).rebind();
        boolean local = host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost") || host.equals("::1");
        e.getChannel().sendMessage("HTTP API address set to **" + host + ":" + port + "**, "
            + (rpm <= 0 ? "unlimited" : rpm + "/min per token") + ". "
            + (local ? "Localhost-only." : "⚠ Exposed beyond localhost — firewall/VPN the port.")).queue();
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static UserAssignment findByName(final String name) {
        for (UserAssignment ua : cfg().users.values()) {
            if (ua.name.equalsIgnoreCase(name)) return ua;
        }
        return null;
    }

    private static String randomToken() {
        final byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        final StringBuilder sb = new StringBuilder(b.length * 2);
        for (final byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
