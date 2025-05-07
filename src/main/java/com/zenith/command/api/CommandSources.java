package com.zenith.command.api;

public final class CommandSources {
    public static final DiscordCommandSource DISCORD = new DiscordCommandSource();
    public static final TerminalCommandSource TERMINAL = new TerminalCommandSource();
    public static final PlayerCommandSource PLAYER = new PlayerCommandSource("Controlling Player");
    public static final PlayerCommandSource SPECTATOR = new PlayerCommandSource("Spectator");
}
