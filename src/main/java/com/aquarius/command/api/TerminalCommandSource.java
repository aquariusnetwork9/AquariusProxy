package com.aquarius.command.api;

import com.aquarius.discord.Embed;
import lombok.Data;

import java.util.List;

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

    @Override
    public void logMultiLine(final List<String> multiLine) {
        CommandOutputHelper.logMultiLineOutputToTerminal(multiLine);
    }
}
