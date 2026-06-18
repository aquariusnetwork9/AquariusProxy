package com.aquarius.command.api;

import com.aquarius.discord.Embed;
import com.aquarius.feature.permissions.Subject;

import java.util.List;

public interface CommandSource {
    String name();
    default String commandPrefix() {
        return "";
    }
    boolean validateAccountOwner(CommandContext ctx);

    /**
     * Resolve the RBAC subject for this source (used when {@code permissions.enabled}). The default is a
     * fully-trusted subject — correct for the console, Discord, and internal automation sources, which are all
     * owner-controlled. {@link PlayerCommandSource} overrides it to resolve the connected player by UUID.
     */
    default Subject resolveSubject(CommandContext ctx) {
        return Subject.allPermissions(name());
    }

    void logEmbed(CommandContext ctx, Embed embed);
    default void logMultiLine(List<String> multiLine) {}
}
