package com.aquarius.command.api;

import com.aquarius.discord.Embed;
import com.aquarius.feature.permissions.Subject;

import static com.aquarius.Globals.PERMISSIONS;

/**
 * Command source for the token-authorized HTTP API. Carries the resolved {@link Subject}, so commands are
 * authorized exactly as that subject (its role/permissions), not as a trusted console. Output is read off the
 * {@link CommandContext} by the HTTP handler after execution, so {@link #logEmbed} is a no-op.
 */
public class ApiCommandSource implements CommandSource {
    private final Subject subject;

    public ApiCommandSource(final Subject subject) {
        this.subject = subject;
    }

    @Override
    public String name() {
        return "API:" + (subject.name() == null || subject.name().isEmpty() ? "token" : subject.name());
    }

    @Override
    public boolean validateAccountOwner(final CommandContext ctx) {
        return PERMISSIONS.allows(subject, "command.manage");
    }

    @Override
    public Subject resolveSubject(final CommandContext ctx) {
        return subject;
    }

    @Override
    public void logEmbed(final CommandContext ctx, final Embed embed) {
        // no-op: the HTTP handler serializes ctx.getEmbed()/getMultiLineOutput() into the response
    }
}
