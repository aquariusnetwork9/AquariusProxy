package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.feature.permissions.PermissionManager;
import com.aquarius.feature.permissions.PermissionsConfig;
import com.aquarius.feature.permissions.Role;
import com.aquarius.feature.permissions.UserAssignment;
import com.aquarius.feature.whitelist.PlayerListsManager;
import com.aquarius.module.impl.RbacApiServer;
import com.aquarius.module.impl.RbacGuard;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.security.SecureRandom;
import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

/**
 * Manage the RBAC system: roles, per-user assignments, and API tokens. Admin-only (MANAGE category → command.manage,
 * which only admin holds — the permission system is admin-exclusive). Both the Discord panel and the ProxyBridge mod
 * GUI drive these operations (directly or via the HTTP /command API).
 */
public class PermsCommand extends Command {
    private static final SecureRandom RANDOM = new SecureRandom();

    private static PermissionsConfig cfg() {
        return CONFIG.server.permissions;
    }

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("perms")
            .category(CommandCategory.MANAGE)
            .description("Role-based access control: roles, user assignments, and API tokens (replaces the whitelist).")
            .usageLines(
                "enable <on/off>            (turn RBAC on/off; replaces the whitelist while on)",
                "api <on/off>               (token-authorized HTTP command API)",
                "status",
                "reload                     (re-sync RBAC modules from config)",
                "user list",
                "user add <name> <role>     (resolve + assign a role)",
                "user role <name> <role>",
                "user remove <name>",
                "user grant <name> <perm>   (e.g. group.combat, module.killaura)",
                "user deny <name> <perm>",
                "user info <name>",
                "token issue <name>         (prints the token once)",
                "token revoke <name> <index>",
                "role list",
                "group list"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("perms")
            .then(literal("enable").then(argument("toggle", toggle()).executes(c -> {
                cfg().enabled = getToggle(c, "toggle");
                MODULE.get(RbacGuard.class).syncEnabledFromConfig();
                MODULE.get(RbacApiServer.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("RBAC " + toggleStrCaps(cfg().enabled)
                    + (cfg().enabled ? " — now replacing the whitelist" : ""));
                return OK;
            })))
            .then(literal("api").then(argument("toggle", toggle()).executes(c -> {
                cfg().api.enabled = getToggle(c, "toggle");
                MODULE.get(RbacApiServer.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("RBAC HTTP API " + toggleStrCaps(cfg().api.enabled))
                    .description(cfg().api.bindHost + ":" + cfg().api.port);
                return OK;
            })))
            .then(literal("status").executes(c -> {
                c.getSource().getEmbed()
                    .title("RBAC status")
                    .addField("Enabled", toggleStr(cfg().enabled), true)
                    .addField("HTTP API", toggleStr(cfg().api.enabled) + " (" + cfg().api.bindHost + ":" + cfg().api.port + ")", true)
                    .addField("Users", String.valueOf(cfg().users.size()), true)
                    .addField("Roles", String.join(", ", cfg().roles.keySet()), false)
                    .addField("Groups", String.join(", ", cfg().groups.keySet()), false)
                    .primaryColor();
                return OK;
            }))
            .then(literal("reload").executes(c -> {
                MODULE.get(RbacGuard.class).syncEnabledFromConfig();
                MODULE.get(RbacApiServer.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("RBAC modules re-synced from config");
                return OK;
            }))
            .then(literal("user")
                .then(literal("list").executes(c -> {
                    if (cfg().users.isEmpty()) {
                        c.getSource().getEmbed().title("No users assigned").primaryColor();
                        return OK;
                    }
                    StringBuilder sb = new StringBuilder();
                    cfg().users.forEach((id, ua) -> sb.append("- ").append(ua.name).append(" : ").append(ua.role)
                        .append(ua.grants.isEmpty() ? "" : " +" + ua.grants).append("\n"));
                    c.getSource().getEmbed().title("Users (" + cfg().users.size() + ")").description(sb.toString().trim()).primaryColor();
                    return OK;
                }))
                .then(literal("add").then(argument("name", word()).then(argument("role", word()).executes(c -> {
                    return assignRole(c.getSource(), getString(c, "name"), getString(c, "role"), true);
                }))))
                .then(literal("role").then(argument("name", word()).then(argument("role", word()).executes(c -> {
                    return assignRole(c.getSource(), getString(c, "name"), getString(c, "role"), false);
                }))))
                .then(literal("remove").then(argument("name", word()).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    cfg().users.values().removeIf(u -> u == ua);
                    c.getSource().getEmbed().title("Removed " + ua.name);
                    return OK;
                })))
                .then(literal("grant").then(argument("name", word()).then(argument("perm", greedyString()).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    String perm = getString(c, "perm").trim();
                    if (!ua.grants.contains(perm)) ua.grants.add(perm);
                    c.getSource().getEmbed().title(ua.name + " grants += " + perm).description("grants: " + ua.grants);
                    return OK;
                }))))
                .then(literal("deny").then(argument("name", word()).then(argument("perm", greedyString()).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    String perm = getString(c, "perm").trim();
                    if (!ua.denies.contains(perm)) ua.denies.add(perm);
                    c.getSource().getEmbed().title(ua.name + " denies += " + perm).description("denies: " + ua.denies);
                    return OK;
                }))))
                .then(literal("info").then(argument("name", word()).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    c.getSource().getEmbed()
                        .title("User " + ua.name)
                        .addField("Role", ua.role, true)
                        .addField("Tokens", String.valueOf(ua.tokens.size()), true)
                        .addField("Connect mode", ua.connectMode, true)
                        .addField("Grants", ua.grants.isEmpty() ? "(none)" : String.join(", ", ua.grants), false)
                        .addField("Denies", ua.denies.isEmpty() ? "(none)" : String.join(", ", ua.denies), false)
                        .primaryColor();
                    return OK;
                }))))
            .then(literal("token")
                .then(literal("issue").then(argument("name", word()).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    String plaintext = randomToken();
                    ua.tokens.add(PermissionManager.sha256Hex(plaintext));
                    c.getSource().setSensitiveInput(true);
                    c.getSource().getEmbed()
                        .title("Token issued for " + ua.name + " (shown once)")
                        .description("`" + plaintext + "`")
                        .successColor();
                    return OK;
                })))
                .then(literal("revoke").then(argument("name", word()).then(argument("index", integer(0)).executes(c -> {
                    UserAssignment ua = findByName(getString(c, "name"));
                    if (ua == null) return notFound(c.getSource(), getString(c, "name"));
                    int idx = getInteger(c, "index");
                    if (idx < 0 || idx >= ua.tokens.size()) {
                        c.getSource().getEmbed().title("No token #" + idx + " for " + ua.name + " (has " + ua.tokens.size() + ")").errorColor();
                        return ERROR;
                    }
                    ua.tokens.remove(idx);
                    c.getSource().getEmbed().title("Revoked token #" + idx + " for " + ua.name + " (" + ua.tokens.size() + " left)");
                    return OK;
                }))))
            )
            .then(literal("role").then(literal("list").executes(c -> {
                StringBuilder sb = new StringBuilder();
                cfg().roles.forEach((r, perms) -> sb.append("- ").append(r).append(": ").append(perms).append("\n"));
                c.getSource().getEmbed().title("Roles").description(sb.toString().trim()).primaryColor();
                return OK;
            })))
            .then(literal("group").then(literal("list").executes(c -> {
                StringBuilder sb = new StringBuilder();
                cfg().groups.forEach((g, perms) -> sb.append("- ").append(g).append(": ").append(perms).append("\n"));
                c.getSource().getEmbed().title("Capability groups").description(sb.toString().trim()).primaryColor();
                return OK;
            })));
    }

    private int assignRole(final CommandContext source, final String name, final String role, final boolean create) {
        if (Role.fromString(role) == Role.NONE) {
            source.getEmbed().title("Unknown role: " + role).description("Use one of: guest, user, operator, admin").errorColor();
            return ERROR;
        }
        UserAssignment ua = findByName(name);
        if (ua == null) {
            if (!create) return notFound(source, name);
            var entry = PlayerListsManager.createPlayerListEntry(name).orElse(null);
            if (entry == null) {
                source.getEmbed().title("Could not resolve username: " + name).errorColor();
                return ERROR;
            }
            ua = new UserAssignment(entry.getUsername(), role.toLowerCase());
            cfg().users.put(entry.getUuid(), ua);
            source.getEmbed().title("Added " + ua.name + " as " + ua.role).successColor();
            return OK;
        }
        ua.role = role.toLowerCase();
        source.getEmbed().title(ua.name + " is now " + ua.role).successColor();
        return OK;
    }

    private UserAssignment findByName(final String name) {
        for (UserAssignment ua : cfg().users.values()) {
            if (ua.name.equalsIgnoreCase(name)) return ua;
        }
        return null;
    }

    private int notFound(final CommandContext source, final String name) {
        source.getEmbed().title("No assigned user: " + name).errorColor();
        return ERROR;
    }

    private static String randomToken() {
        final byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        final StringBuilder sb = new StringBuilder(b.length * 2);
        for (final byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    @Override
    public void defaultEmbed(final Embed embedBuilder) {
        embedBuilder.addField("RBAC", toggleStr(cfg().enabled), true)
            .addField("Users", String.valueOf(cfg().users.size()), true)
            .primaryColor();
    }
}
