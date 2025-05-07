package com.zenith.command.api;

import com.zenith.discord.Embed;
import lombok.Data;

@Data
public class TerminalCommandSource implements CommandSource {
    @Override
    public String name() {
        return "Terminal";
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
