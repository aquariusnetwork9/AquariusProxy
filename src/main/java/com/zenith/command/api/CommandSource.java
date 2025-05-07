package com.zenith.command.api;

import com.zenith.discord.Embed;

public interface CommandSource {
    String name();
    default String commandPrefix() {
        return "";
    }
    boolean validateAccountOwner(CommandContext ctx);
    void logEmbed(CommandContext ctx, Embed embed);
}
