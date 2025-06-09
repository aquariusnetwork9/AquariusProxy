package com.zenith.util;


import java.util.Set;

import static com.zenith.Globals.CONFIG;

public class ChatUtil {

    public static String sanitizeChatMessage(final String input) {
        StringBuilder stringbuilder = new StringBuilder();
        for (char c0 : input.toCharArray()) {
            if (isAllowedChatCharacter(c0)) {
                stringbuilder.append(c0);
            }
        }
        return stringbuilder.toString();
    }

    public static boolean isAllowedChatCharacter(char c0) {
        return c0 != 167 && c0 >= 32 && c0 != 127;
    }

    private static final Set<String> knownWhisperCommands = Set.of(
        "w", "whisper", "msg", "minecraft:msg", "tell", "r"
    );
    public static boolean isWhisperCommand(String command) {
        if (CONFIG.client.extra.whisperCommand.equals(command)) return true;
        return knownWhisperCommands.contains(command);
    }
}
