package com.aquarius.command.api;

import com.aquarius.discord.Embed;

import java.util.List;

public interface CommandSource {
    String name();
    default String commandPrefix() {
        return "";
    }
    boolean validateAccountOwner(CommandContext ctx);
    void logEmbed(CommandContext ctx, Embed embed);
    default void logMultiLine(List<String> multiLine) {}
}
