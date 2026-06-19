package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.feature.permissions.Subject;
import com.aquarius.module.impl.AutoLoadModule;

import java.util.List;
import java.util.UUID;

import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.CustomStringArgumentType.getString;
import static com.aquarius.command.brigadier.CustomStringArgumentType.wordWithChars;

/**
 * Self-scoped pearl pull for non-admin members: pulls the <b>caller's own</b> stored pearl (PearlPlus), gated by
 * RBAC {@code pearl.pull} rather than owner/{@code command.manage}. The caller's identity is the resolved command
 * subject — their token's subject over the HTTP command API, or the player in-game — so a member can pull without
 * naming themselves and without being the account owner. This is the member-facing counterpart to the owner-only
 * {@code pearlplus load <name> <id>}; together with the ProxyBridge channel it lets a remote member (token + IP)
 * pull their pearl entirely over the backend. Shares {@link AutoLoadModule#requestPull} with the whisper path.
 */
public class PearlPullCommand extends Command {

    /** Member-facing: only requires {@code pearl.pull} (gated by the central command gate when RBAC is enabled). */
    @Override
    public String requiredPermission(final List<String> commandPath) {
        return "pearl.pull";
    }

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("pearlpull")
            .category(CommandCategory.MODULE)
            .description("""
            Pull YOUR OWN stored pearl (PearlPlus). Gated by the `pearl.pull` permission.

            Resolves the caller's identity automatically, so a member with a token can pull over the HTTP API
            (or in-game) without naming themselves. Admins use `pearlplus load <name> <id>` to pull anyone's.
            """)
            .usageLines("", "<pearlId>")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("pearlpull")
            .executes(c -> { return pull(c.getSource(), null); })
            .then(argument("pearlId", wordWithChars()).executes(c -> { return pull(c.getSource(), getString(c, "pearlId")); }));
    }

    private int pull(final CommandContext ctx, final String pearlId) {
        final Subject subject = ctx.getSource().resolveSubject(ctx);
        final UUID uuid = subject.uuid();
        if (uuid == null) {
            ctx.getEmbed().title("Pearl Pull").errorColor()
                .addField("Error", "No player identity for this caller — use `pearlplus load <name> <id>` instead.", false);
            return ERROR;
        }
        final var result = MODULE.get(AutoLoadModule.class).requestPull(uuid, subject.name(), pearlId);
        ctx.getEmbed().title("Pearl Pull").addField(result.started() ? "Loading" : "Result", result.message(), false);
        if (result.started()) ctx.getEmbed().successColor(); else ctx.getEmbed().errorColor();
        return result.started() ? OK : ERROR;
    }
}
