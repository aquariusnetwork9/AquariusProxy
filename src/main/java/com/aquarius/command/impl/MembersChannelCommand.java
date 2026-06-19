package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.*;
import com.aquarius.discord.Panels;
import com.aquarius.feature.permissions.Role;
import com.aquarius.util.MentionUtil;
import com.aquarius.util.config.Config;

import java.util.Locale;
import java.util.regex.Pattern;

import static com.aquarius.Globals.*;
import static com.aquarius.command.brigadier.CustomStringArgumentType.getString;
import static com.aquarius.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;
import static java.util.Arrays.asList;

/**
 * Configures the read-only Discord <b>members channel</b> ({@code CONFIG.discord.membersChannel}): a second
 * channel that mirrors a curated, coordinate-scrubbed subset of notifications for non-admin members. RBAC-gated
 * — the channel carries an audience role and each notice / sensitive detail carries a minimum role. Commands are
 * never accepted in that channel; this command (admin-only, terminal/Discord admin channel) drives its config.
 */
public class MembersChannelCommand extends Command {
    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("<#\\d+>");
    private static final String ROLE_HINT = "none / guest / user / operator / admin";

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("members")
            .category(CommandCategory.MANAGE)
            .description("""
            Configures the read-only Discord members channel.

            A second channel that mirrors a curated, coordinate-scrubbed subset of notifications
            (online, offline, queue, prio, pearl-pulled) for non-admin members. RBAC-gated: the channel has an
            audience role, and each notice + sensitive detail (coords, proxy IP) has a minimum role. A notice
            shows only when the audience role is at least the notice's role; sensitive fields are stripped below
            their threshold. Commands are never accepted here — that stays the admin channel.

            Ships off. Set the channel and audience, then turn it on. Restrict who can see the channel using
            Discord's own per-role channel permissions.
            """)
            .usageLines(
                "on/off",
                "channel <channelId>",
                "audience <role>",
                "notice <online|offline|queue|prio|pearlpull|visualrange> <role|off>",
                "coords <role>",
                "proxyip <role>",
                "visualrangecoords on/off",
                "panel"
            )
            .build();
    }

    private static Config.Discord.MembersChannel cfg() { return CONFIG.discord.membersChannel; }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("members")
            .requires(Command::validateAccountOwner)
            .requires(c -> Command.validateCommandSource(c, asList(CommandSources.DISCORD, CommandSources.TERMINAL)))
            .then(argument("toggle", toggle()).executes(c -> {
                boolean on = getToggle(c, "toggle");
                if (on && cfg().channelId.isEmpty()) {
                    fail(c, "Error", "Set the members channel first: `members channel <channelId>`");
                    return OK;
                }
                cfg().enable = on;
                c.getSource().getEmbed().title("Members Channel " + toggleStrCaps(on));
                return OK;
            }))
            .then(literal("channel").then(argument("channelId", wordWithChars()).executes(c -> {
                String channelId = getString(c, "channelId");
                if (CHANNEL_ID_PATTERN.matcher(channelId).matches())
                    channelId = channelId.substring(2, channelId.length() - 1);
                try {
                    Long.parseUnsignedLong(channelId);
                } catch (final Exception e) {
                    fail(c, "Invalid Channel ID", "The channel ID provided is invalid");
                    return OK;
                }
                if (channelId.equals(CONFIG.discord.channelId)) {
                    fail(c, "Invalid Channel ID", "The members channel must differ from the admin channel");
                    return OK;
                }
                if (channelId.equals(CONFIG.discord.chatRelay.channelId)) {
                    fail(c, "Invalid Channel ID", "The members channel must differ from the chat-relay channel");
                    return OK;
                }
                cfg().channelId = channelId;
                c.getSource().getEmbed().title("Members channel set!")
                    .description("Mirrors post here immediately — no bot restart needed.");
                return OK;
            })))
            .then(literal("audience").then(argument("role", wordWithChars()).executes(c -> {
                String role = role(c);
                if (!isKnownRole(role)) return invalidRole(c);
                cfg().audienceRole = role;
                c.getSource().getEmbed().title("Audience role set to " + role)
                    .description("This channel now shows everything classified at " + role + " or below.");
                return OK;
            })))
            .then(literal("notice").then(argument("notice", wordWithChars()).then(argument("role", wordWithChars()).executes(c -> {
                String notice = getString(c, "notice").trim().toLowerCase(Locale.ROOT);
                String role = role(c);
                boolean off = role.equals("off") || role.isBlank();
                if (!off && !isKnownRole(role)) return invalidRole(c);
                if (!setNoticeRole(notice, off ? "off" : role)) {
                    fail(c, "Unknown notice", "Use one of: online, offline, queue, prio, pearlpull, visualrange");
                    return OK;
                }
                c.getSource().getEmbed().title("Notice updated")
                    .description("`" + notice + "` → " + (off ? "disabled" : "min role " + role));
                return OK;
            }))))
            .then(literal("coords").then(argument("role", wordWithChars()).executes(c -> {
                String role = role(c);
                if (!isKnownRole(role)) return invalidRole(c);
                cfg().coordsRole = role;
                c.getSource().getEmbed().title("Coordinate visibility set to " + role + "+")
                    .description("Coordinates are stripped from this channel unless the audience is " + role + " or higher.");
                return OK;
            })))
            .then(literal("proxyip").then(argument("role", wordWithChars()).executes(c -> {
                String role = role(c);
                if (!isKnownRole(role)) return invalidRole(c);
                cfg().proxyIpRole = role;
                c.getSource().getEmbed().title("Proxy-IP visibility set to " + role + "+")
                    .description("The proxy IP is stripped from this channel unless the audience is " + role + " or higher.");
                return OK;
            })))
            .then(literal("visualrangecoords").then(argument("toggle", toggle()).executes(c -> {
                cfg().visualRangeCoords = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Visual-range coords " + toggleStrCaps(cfg().visualRangeCoords))
                    .description(cfg().visualRangeCoords
                        ? "Visual-range coordinates now follow the `coords` threshold in the members channel."
                        : "Visual-range coordinates are always stripped from the members channel.");
                return OK;
            })))
            .then(literal("panel").executes(c -> {
                c.getSource().getData().put("noDefaultEmbed", true);
                if (!DISCORD.openPanel(Panels.MEMBERS)) {
                    c.getSource().getEmbed().title("Discord bot not running").errorColor();
                }
                return OK;
            }));
    }

    @Override
    public void defaultHandler(final CommandContext c) {
        if (!c.getData().containsKey("noDefaultEmbed")) {
            var cfg = cfg();
            c.getEmbed()
                .addField("Members Channel", toggleStr(cfg.enable), true)
                .addField("Channel", getChannelMention(cfg.channelId), true)
                .addField("Audience Role", cfg.audienceRole, true)
                .addField("Online", noticeStr(cfg.onlineRole), true)
                .addField("Offline", noticeStr(cfg.offlineRole), true)
                .addField("Queue", noticeStr(cfg.queueRole), true)
                .addField("Prio", noticeStr(cfg.prioRole), true)
                .addField("Pearl Pull", noticeStr(cfg.pearlPullRole), true)
                .addField("Visual Range", noticeStr(cfg.visualRangeRole), true)
                .addField("Coords shown to", cfg.coordsRole + "+", true)
                .addField("Proxy IP shown to", cfg.proxyIpRole + "+", true)
                .addField("Visual-range coords", toggleStr(cfg.visualRangeCoords), true);
        }
        c.getEmbed().primaryColor();
    }

    // ---------------------------------------------------------------- helpers

    private static String role(final com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        return getString(c, "role").trim().toLowerCase(Locale.ROOT);
    }

    /** Emit a standalone error embed (suppresses the default status dump and keeps the error color). */
    private static void fail(final com.mojang.brigadier.context.CommandContext<CommandContext> c, String title, String desc) {
        c.getSource().getData().put("noDefaultEmbed", true);
        c.getSource().getEmbed().title(title).description(desc).errorColor();
    }

    private static int invalidRole(final com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        fail(c, "Unknown role", "Use one of: " + ROLE_HINT);
        return OK;
    }

    private static String noticeStr(String role) {
        return (role == null || role.isBlank() || role.equalsIgnoreCase("off")) ? "disabled" : role;
    }

    private static boolean setNoticeRole(String notice, String value) {
        switch (notice) {
            case "online" -> cfg().onlineRole = value;
            case "offline" -> cfg().offlineRole = value;
            case "queue" -> cfg().queueRole = value;
            case "prio" -> cfg().prioRole = value;
            case "pearlpull", "pearl", "pearl_pull" -> cfg().pearlPullRole = value;
            case "visualrange", "visual_range", "vr" -> cfg().visualRangeRole = value;
            default -> { return false; }
        }
        return true;
    }

    private static boolean isKnownRole(String r) {
        if (r == null) return false;
        for (Role role : Role.values()) if (role.name().equalsIgnoreCase(r.trim())) return true;
        return false;
    }

    private String getChannelMention(final String channelId) {
        if (channelId == null || channelId.isBlank()) return "(unset)";
        try {
            return MentionUtil.forChannel(channelId);
        } catch (final Exception e) {
            return channelId;
        }
    }
}
