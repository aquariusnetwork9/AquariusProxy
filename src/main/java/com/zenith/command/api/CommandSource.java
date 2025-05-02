package com.zenith.command.api;

import com.zenith.discord.Embed;
import lombok.Data;

import java.util.function.Supplier;

@Data
public abstract class CommandSource {
    private final String name;
    private final Supplier<String> prefixSupplier;

    public CommandSource(final String name, final Supplier<String> prefixSupplier) {
        this.name = name;
        this.prefixSupplier = prefixSupplier;
    }

    public abstract boolean validateAccountOwner(CommandContext ctx);

    public abstract void logEmbed(CommandContext ctx, Embed embed);
}
