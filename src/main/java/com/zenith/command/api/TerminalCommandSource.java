package com.zenith.command.api;

import com.zenith.discord.Embed;

public class TerminalCommandSource extends CommandSource {
    public TerminalCommandSource() {
        super("Terminal", () -> "");
    }

    @Override
    public boolean validateAccountOwner(CommandContext ctx) {
        return true;
    }

    @Override
    public void logEmbed(final CommandContext ctx, final Embed embed) {
        CommandOutputHelper.logEmbedOutputToTerminal(embed);
    }
}
