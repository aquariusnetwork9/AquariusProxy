package com.aquarius.discord;

import com.aquarius.feature.permissions.Role;
import com.aquarius.util.config.Config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DISCORD;

/**
 * Mirrors a curated, RBAC-gated, coord-scrubbed subset of notifications into the read-only "members channel"
 * ({@code CONFIG.discord.membersChannel}). The admin channel is untouched — every mirror scrubs a {@link Embed#copy()}.
 *
 * <p>Gate: the channel carries an audience role; each {@link Notice} and each sensitive detail (coords, proxy IP)
 * carries a configured minimum role. A notice posts iff {@code audience.atLeast(noticeMinRole)}; a sensitive field
 * is stripped unless {@code audience.atLeast(detailMinRole)}. It only borrows the {@link Role} lattice for
 * classification, so it works whether or not {@code permissions.enabled} is on.
 *
 * <p>Call {@link #mirror} <i>after</i> the admin-channel send at each whitelisted notification site.
 */
public final class MembersFeed {
    private MembersFeed() {}

    public enum Notice { ONLINE, OFFLINE, QUEUE, PRIO, PEARL_PULL, VISUAL_RANGE }

    /** Field names (lowercased) considered location data — stripped below the coords threshold. */
    private static final Set<String> COORD_FIELDS = Set.of("coordinates", "position", "our position", "distance");
    /** Field names (lowercased) considered infra data — stripped below the proxy-IP threshold. */
    private static final Set<String> IP_FIELDS = Set.of("proxy ip", "ip");
    /** A coordinate tuple like {@code [123, 64, -456]}, optionally spoiler-wrapped ({@code ||...||}). */
    private static final Pattern COORD_TUPLE = Pattern.compile("\\|{0,2}\\[\\s*-?\\d[\\d,\\s.-]*]\\|{0,2}");

    /** Mirror an admin-channel embed to the members channel, if the channel's audience is cleared for this notice. */
    public static void mirror(Notice notice, Embed adminEmbed) {
        final Config.Discord.MembersChannel cfg = CONFIG.discord.membersChannel;
        if (!cfg.enable || cfg.channelId == null || cfg.channelId.isBlank()) return;
        final Role audience = Role.fromString(cfg.audienceRole);
        final Role need = noticeMinRole(notice, cfg);
        if (need == null || !audience.atLeast(need)) return;     // notice disabled, or audience not cleared for it
        // Visual-range coords are a hard off-switch independent of the coordsRole threshold (the bot's own location).
        final boolean forceStripCoords = notice == Notice.VISUAL_RANGE && !cfg.visualRangeCoords;
        DISCORD.sendEmbedMessageToChannelIdOrDrop(cfg.channelId, scrub(adminEmbed.copy(), audience, cfg, forceStripCoords));
    }

    /** The notice's configured minimum role, or {@code null} if the notice is disabled (blank / {@code "off"}). */
    private static Role noticeMinRole(Notice n, Config.Discord.MembersChannel cfg) {
        final String s = switch (n) {
            case ONLINE -> cfg.onlineRole;
            case OFFLINE -> cfg.offlineRole;
            case QUEUE -> cfg.queueRole;
            case PRIO -> cfg.prioRole;
            case PEARL_PULL -> cfg.pearlPullRole;
            case VISUAL_RANGE -> cfg.visualRangeRole;
        };
        if (s == null || s.isBlank() || s.equalsIgnoreCase("off")) return null;
        return Role.fromString(s);
    }

    /** Strip sensitive fields/values from a (copied) embed for the given audience. Returns the same instance. */
    private static Embed scrub(Embed e, Role audience, Config.Discord.MembersChannel cfg, boolean forceStripCoords) {
        final boolean stripCoords = forceStripCoords || !audience.atLeast(Role.fromString(cfg.coordsRole));
        final boolean stripIp = !audience.atLeast(Role.fromString(cfg.proxyIpRole));

        if (stripCoords || stripIp) {
            e.fields().removeIf(f -> {
                final String name = f.name() == null ? "" : f.name().toLowerCase(Locale.ROOT).trim();
                return (stripCoords && COORD_FIELDS.contains(name)) || (stripIp && IP_FIELDS.contains(name));
            });
        }
        if (stripCoords) {
            if (e.description() != null) {
                e.description(COORD_TUPLE.matcher(e.description()).replaceAll("[redacted]"));
            }
            // Value-level backstop: scrub any coord tuple embedded in a field that survived the name filter.
            final List<Embed.Field> fs = e.fields();
            for (int i = 0; i < fs.size(); i++) {
                final Embed.Field f = fs.get(i);
                if (f.value() != null && f.value().indexOf('[') >= 0) {
                    final String v = COORD_TUPLE.matcher(f.value()).replaceAll("[redacted]");
                    if (!v.equals(f.value())) fs.set(i, new Embed.Field(f.name(), v, f.inline()));
                }
            }
        }
        return e;
    }
}
