package com.aquarius.discord;

import com.aquarius.util.MentionUtil;
import com.aquarius.util.config.Config;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.aquarius.Globals.CONFIG;

/**
 * Discord interactive panel for the read-only members channel ({@code CONFIG.discord.membersChannel}) — the
 * Discord-shaped counterpart to the {@code /members} command. Lets an admin, from chat: toggle the feed, set the
 * channel (modal), pick the audience role, cycle the coords / proxy-IP visibility thresholds, and set each
 * notice's minimum role (pick a notice, then pick its role). All controls mutate the config directly (the config
 * IS the panel state); the only extra state is the in-memory {@link #selectedNotice}. Owner-gated and
 * prefix-routed by {@link DiscordPanel}. Posted with {@code members panel}.
 */
public final class MembersPanel extends DiscordPanel {

    private static final String PREFIX = "members:";
    private static final String ENABLE = "members:enable", CHANNEL = "members:channel",
        COORDS = "members:coords", PROXYIP = "members:proxyip", VRCOORDS = "members:vrcoords",
        AUDIENCE = "members:audience", NOTICESEL = "members:noticesel", NOTICEROLE = "members:noticerole",
        CHANNELMODAL = "members:channelmodal";

    private static final String[] AUDIENCE_ROLES = {"none", "guest", "user", "operator", "admin"};
    private static final String[] THRESHOLD_ROLES = {"guest", "user", "operator", "admin"};
    private static final String[] NOTICES = {"online", "offline", "queue", "prio", "pearlpull", "visualrange"};

    /** Which notice the admin is currently editing (in-memory UI state only). */
    private String selectedNotice;

    @Override protected String prefix() { return PREFIX; }

    private static Config.Discord.MembersChannel cfg() { return CONFIG.discord.membersChannel; }

    // ---------------------------------------------------------------- render

    @Override
    protected Embed embed() {
        var cfg = cfg();
        Embed e = new Embed().primaryColor().title("Members Channel (read-only feed)")
            .addField("Status", cfg.enable ? "📣 ON" : "off", true)
            .addField("Channel", channelMention(cfg.channelId), true)
            .addField("Audience", cfg.audienceRole, true)
            .addField("Coords shown to", cfg.coordsRole + "+", true)
            .addField("Proxy IP shown to", cfg.proxyIpRole + "+", true)
            .addField("Notices (min role to mirror)",
                "online `" + show(cfg.onlineRole) + "` · offline `" + show(cfg.offlineRole)
                + "` · queue `" + show(cfg.queueRole) + "` · prio `" + show(cfg.prioRole)
                + "` · pearl `" + show(cfg.pearlPullRole) + "` · visualrange `" + show(cfg.visualRangeRole)
                + "` (coords " + (cfg.visualRangeCoords ? "on" : "off") + ")", false);
        e.description("Mirrors online/offline/queue/prio/pearl notices to a second channel, coordinate-scrubbed by "
            + "audience role. Read-only — commands stay in the admin channel. Restrict who can see the channel with "
            + "Discord's own per-role channel permissions."
            + (selectedNotice != null ? "\n\n▶ Editing **" + selectedNotice + "** — pick its min role below." : ""));
        return e;
    }

    @Override
    protected List<ActionRow> components() {
        var cfg = cfg();
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            cfg.enable ? Button.success(ENABLE, "Feed: ON") : Button.secondary(ENABLE, "Feed: off"),
            Button.secondary(CHANNEL, "⚙ Channel"),
            Button.secondary(COORDS, "Coords ≥ " + cfg.coordsRole),
            Button.secondary(PROXYIP, "Proxy IP ≥ " + cfg.proxyIpRole),
            cfg.visualRangeCoords ? Button.success(VRCOORDS, "VR coords: ON") : Button.secondary(VRCOORDS, "VR coords: off")
        ));

        var audSel = StringSelectMenu.create(AUDIENCE).setPlaceholder("Audience role: " + cfg.audienceRole);
        for (String r : AUDIENCE_ROLES) {
            if (r.equals(cfg.audienceRole)) audSel.addOption(r, r, "current");
            else audSel.addOption(r, r);
        }
        rows.add(ActionRow.of(audSel.build()));

        var noticeSel = StringSelectMenu.create(NOTICESEL).setPlaceholder("Edit a notice…");
        for (String n : NOTICES) {
            boolean isSel = n.equals(selectedNotice);
            noticeSel.addOption(n, n, "min role: " + show(noticeRole(n)) + (isSel ? "  ✓ editing" : ""));
        }
        rows.add(ActionRow.of(noticeSel.build()));

        if (selectedNotice != null) {
            var roleSel = StringSelectMenu.create(NOTICEROLE).setPlaceholder("Min role for " + selectedNotice + "…");
            roleSel.addOption("off (disable)", "off", "never mirror this notice");
            for (String r : THRESHOLD_ROLES) roleSel.addOption(r, r);
            roleSel.addOption("none (always)", "none", "show to any audience");
            rows.add(ActionRow.of(roleSel.build()));
        }
        return rows;
    }

    private Modal channelModal() {
        TextInput id = TextInput.create("members:channelid", TextInputStyle.SHORT).setRequired(true)
            .setValue(cfg().channelId).setPlaceholder("Discord channel ID (must differ from the admin channel)").build();
        return Modal.create(CHANNELMODAL, "Members channel")
            .addComponents(Label.of("Channel ID", id))
            .build();
    }

    // ---------------------------------------------------------------- interactions

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var cfg = cfg();
        switch (e.getComponentId()) {
            case ENABLE -> {
                if (!cfg.enable && cfg.channelId.isBlank()) {
                    e.reply("Set a channel first (⚙ Channel).").setEphemeral(true).queue();
                    return false;
                }
                cfg.enable = !cfg.enable;
            }
            case CHANNEL -> { e.replyModal(channelModal()).queue(); return false; }
            case COORDS -> cfg.coordsRole = cycle(cfg.coordsRole);
            case PROXYIP -> cfg.proxyIpRole = cycle(cfg.proxyIpRole);
            case VRCOORDS -> cfg.visualRangeCoords = !cfg.visualRangeCoords;
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        switch (e.getComponentId()) {
            case AUDIENCE -> { cfg().audienceRole = e.getValues().get(0); return true; }
            case NOTICESEL -> { selectedNotice = e.getValues().get(0); return true; }
            case NOTICEROLE -> {
                if (selectedNotice != null) setNoticeRole(selectedNotice, e.getValues().get(0));
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (!CHANNELMODAL.equals(e.getModalId())) return false;
        String id = e.getValue("members:channelid").getAsString().trim();
        if (id.matches("<#\\d+>")) id = id.substring(2, id.length() - 1);
        try { Long.parseUnsignedLong(id); }
        catch (Exception ex) { e.reply("Invalid channel ID.").setEphemeral(true).queue(); return false; }
        if (id.equals(CONFIG.discord.channelId) || id.equals(CONFIG.discord.chatRelay.channelId)) {
            e.reply("The members channel must differ from the admin and chat-relay channels.").setEphemeral(true).queue();
            return false;
        }
        cfg().channelId = id;
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static String noticeRole(String notice) {
        return switch (notice) {
            case "online" -> cfg().onlineRole;
            case "offline" -> cfg().offlineRole;
            case "queue" -> cfg().queueRole;
            case "prio" -> cfg().prioRole;
            case "pearlpull" -> cfg().pearlPullRole;
            case "visualrange" -> cfg().visualRangeRole;
            default -> "";
        };
    }

    private static void setNoticeRole(String notice, String value) {
        switch (notice) {
            case "online" -> cfg().onlineRole = value;
            case "offline" -> cfg().offlineRole = value;
            case "queue" -> cfg().queueRole = value;
            case "prio" -> cfg().prioRole = value;
            case "pearlpull" -> cfg().pearlPullRole = value;
            case "visualrange" -> cfg().visualRangeRole = value;
            default -> { }
        }
    }

    private static String show(String role) {
        return (role == null || role.isBlank() || role.equalsIgnoreCase("off")) ? "off" : role;
    }

    /** Cycle a threshold role guest→user→operator→admin→guest. */
    private static String cycle(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "guest" -> "user";
            case "user" -> "operator";
            case "operator" -> "admin";
            default -> "guest";
        };
    }

    private static String channelMention(String channelId) {
        if (channelId == null || channelId.isBlank()) return "(unset)";
        try { return MentionUtil.forChannel(channelId); }
        catch (Exception e) { return channelId; }
    }
}
