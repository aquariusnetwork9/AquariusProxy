package com.aquarius.feature.sixb6t;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deciding whether a system chat line is 6b6t's OWN teleport-request notice, and who it names.
 *
 * Ported from the (already live-proven) {@code tpy-accept} Fabric mod's {@code RequestParser},
 * built from live capture on 6b6t. This is not a naive "does the line contain a name" check: on
 * 6b6t, player chat reaches the client as a SYSTEM message, so the delivery path alone cannot
 * tell a server notice from somebody typing. Without the chat-shape gate below, any player could
 * say "SomeName wants to teleport to you" in public chat and make this fire a command.
 *
 * Two gates, both of which must pass:
 * 1. the line must not have the shape of player chat ({@code Name >> text}, {@code <Name> text})
 * 2. it must match the server's own request wording, and the name must fit Minecraft's name shape
 */
public final class SixB6TTpaRequestParser {
    private SixB6TTpaRequestParser() {}

    /** Legacy formatting codes, stripped before matching so colour cannot break an anchor. */
    private static final Pattern COLOUR_CODES = Pattern.compile("§[0-9A-Za-z]");

    /**
     * The server's wording, captured live:
     *   srymsjackson wants to teleport to you.
     *   Type /tpy srymsjackson to accept or /tpn srymsjackson to deny.
     * Anchored at the start so "...and then someone wants to teleport to you" cannot match.
     */
    private static final Pattern REQUEST =
        Pattern.compile("^([A-Za-z0-9_]{3,16}) wants to teleport to you\\b");

    /**
     * The second sentence, matched anywhere in the line. It carries the accept syntax itself,
     * which makes it a strong server-only signature, and it survives a prefix the anchored form
     * would miss (the two sentences also arrive as one multi-line message, where this is the
     * fallback).
     */
    private static final Pattern INSTRUCTION =
        Pattern.compile("\\bType /tpy ([A-Za-z0-9_]{3,16}) to accept\\b");

    /** 6b6t's guillemet chat format and vanilla's {@code <Name>}, both anchored, rank prefix allowed. */
    private static final Pattern[] PLAYER_CHAT_SHAPES = {
        Pattern.compile("^(?:\\[[^]]*]\\s*)*[A-Za-z0-9_]{3,16}\\s*»"),
        Pattern.compile("^(?:\\[[^]]*]\\s*)*<[A-Za-z0-9_]{3,16}>"),
    };

    /** Does this system message look like a PLAYER talking rather than the server? */
    public static boolean looksLikePlayerChat(String text) {
        String trimmed = text.trim();
        for (Pattern shape : PLAYER_CHAT_SHAPES) {
            if (shape.matcher(trimmed).find()) return true;
        }
        return false;
    }

    /**
     * The player named in a genuine teleport request, or null if this line is not one. Pure --
     * no state touched, so it is safe to call on every incoming system chat message.
     */
    public static String requesterIn(String rawLine) {
        String line = COLOUR_CODES.matcher(rawLine).replaceAll("").trim();
        if (line.isEmpty() || looksLikePlayerChat(line)) return null;

        var request = REQUEST.matcher(line);
        if (request.find()) return request.group(1);

        var instruction = INSTRUCTION.matcher(line);
        if (instruction.find()) return instruction.group(1);

        return null;
    }
}
