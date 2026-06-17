package com.aquarius.command.api;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.aquarius.command.brigadier.CaseInsensitiveLiteralArgumentBuilder;
import com.aquarius.command.brigadier.EnumStringArgumentType;
import com.aquarius.command.brigadier.ZRequiredArgumentBuilder;
import com.aquarius.discord.Embed;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.aquarius.Globals.DEFAULT_LOG;
import static com.aquarius.Globals.PERMISSIONS;
import static com.aquarius.Globals.TERMINAL_LOG;

public abstract class Command {
    public static <T> ZRequiredArgumentBuilder<CommandContext, T> argument(String name, ArgumentType<T> type) {
        return ZRequiredArgumentBuilder.argument(name, type);
    }

    // command return codes
    public static final int OK = 1;
    public static final int ERROR = -1;

    public static boolean validateAccountOwner(final CommandContext context) {
        try {
            // When RBAC is enabled, the legacy owner-gate maps to the management permission (operator/admin).
            // When disabled, fall back to the original owner-or-whitelisted check (zero behavior change).
            if (PERMISSIONS.isEnabled()) {
                var subject = context.getSource().resolveSubject(context);
                boolean allowed = PERMISSIONS.allows(subject, "command.manage");
                if (!allowed) {
                    context.getEmbed()
                        .title("Not Authorized!")
                        .addField("Error", "You lack permission `command.manage` (requires operator or admin).", false)
                        .errorColor();
                }
                return allowed;
            }
            var allowed = context.getSource().validateAccountOwner(context);
            if (!allowed) {
                context.getEmbed()
                    .title("Not Authorized!")
                    .errorColor();
            }
            return allowed;
        } catch (final Throwable e) {
            DEFAULT_LOG.error("Error validating command account owner authorization", e);
            return false;
        }
    }

    public static boolean validateCommandSource(final CommandContext context, final List<CommandSource> allowedSources) {
        var allowed = allowedSources.contains(context.getSource());
        if (!allowed)
            context.getEmbed()
                .title("Not Authorized!")
                .addField("Error",
                          "Command source: `" + context.getSource().name()
                              + "` is not authorized to execute this command!", false);
        return allowed;
    }

    public static boolean validateCommandSource(final CommandContext context, final CommandSource allowedSource) {
        var allowed = allowedSource.equals(context.getSource());
        if (!allowed)
            context.getEmbed()
                .title("Not Authorized!")
                .addField("Error",
                          "Command source: `" + context.getSource().name()
                              + "` is not authorized to execute this command!", false);
        return allowed;
    }

    public static CaseInsensitiveLiteralArgumentBuilder<CommandContext> literal(String literal) {
        return CaseInsensitiveLiteralArgumentBuilder.literal(literal);
    }

    public static CaseInsensitiveLiteralArgumentBuilder<CommandContext> literal(String literal, CommandErrorHandler errorHandler) {
        return literal(literal).withErrorHandler(errorHandler);
    }

    public static CaseInsensitiveLiteralArgumentBuilder<CommandContext> requires(String literal, Predicate<CommandContext> requirement) {
        return literal(literal).requires(requirement);
    }

    public static EnumStringArgumentType enumStrings(String... strings) {
        return EnumStringArgumentType.enumStrings(strings);
    }

    public static EnumStringArgumentType enumStrings(Collection<String> strings) {
        return EnumStringArgumentType.enumStrings(strings);
    }

    public static EnumStringArgumentType enumStrings(Enum<?>[] enumValues) {
        return EnumStringArgumentType.enumStrings(enumValues);
    }

    public static String toggleStr(boolean state) {
        return state ? "on" : "off";
    }

    public static String toggleStrCaps(boolean state) {
        return state ? "On!" : "Off!";
    }

    /**
     * Required. Registers {@link CommandUsage}
     */
    public abstract CommandUsage commandUsage();

    /**
     * The RBAC permission this command requires. Defaults to its category ({@code command.core|info|manage|module});
     * override for a finer per-command permission. Used by the central command gate (a later phase); today
     * enforcement is via the {@link #validateAccountOwner} chokepoint that owner-level commands already call.
     */
    public String requiredPermission() {
        return "command." + commandUsage().getCategory().name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Required. Register a {@link #command}
     */
    public abstract LiteralArgumentBuilder<CommandContext> register();

    /**
     * Override to populate the embed builder after every non-throwing execution, including both success and error cases.
     * Don't include sensitive info, there is no permission validation.
     */
    public void defaultEmbed(final Embed builder) {}

    /**
     * Called after every non-throwing execution, including both successes and error cases, overridable.
     */
    public void defaultHandler(final CommandContext context) {
        defaultEmbed(context.getEmbed());
    }

    public CaseInsensitiveLiteralArgumentBuilder<CommandContext> command(String literal) {
        return literal(literal)
            .withErrorHandler(this::defaultErrorHandler)
            .withSuccessHandler(this::defaultSuccessHandler)
            .withExecutionErrorHandler(this::defaultExecutionErrorHandler)
            .withExecutionExceptionHandler(this::defaultExecutionExceptionHandler);
    }

    /**
     * Workaround for no-arg redirect nodes
     * see https://github.com/Mojang/brigadier/issues/46
     * 4 years and no official fix T.T
     */
    public LiteralArgumentBuilder<CommandContext> redirect(String literal, final CommandNode<CommandContext> destination) {
        final LiteralArgumentBuilder<CommandContext> builder = command(literal)
                .requires(destination.getRequirement())
                .forward(destination.getRedirect(), destination.getRedirectModifier(), destination.isFork())
                .executes(destination.getCommand());
        for (final CommandNode<CommandContext> child : destination.getChildren()) {
            builder.then(child);
        }
        return builder;
    }

    public void defaultSuccessHandler(CommandContext context) {
        defaultHandler(context);
    }

    public void defaultErrorHandler(Map<CommandNode<CommandContext>, CommandSyntaxException> exceptions, CommandContext context) {
        for (var e : exceptions.values()) {
            context.getEmbed()
                .addField("Error", e.getMessage());
        }
        defaultHandler(context);
        if (!context.getEmbed().isTitlePresent()) {
            context.getEmbed()
                .title("Invalid command usage");
        }
        context.getEmbed()
                .addField("Usage", commandUsage().serialize(context.getSource()), false)
                .errorColor();
    }

    public void defaultExecutionErrorHandler(CommandContext commandContext) {
        defaultHandler(commandContext);
        commandContext.getEmbed()
            .errorColor();
    }

    public void defaultExecutionExceptionHandler(CommandContext commandContext, Throwable e) {
        if (e instanceof CommandSyntaxException)
            return; // handled in error handler, safe to swallow, will happen on command without args like doing `clientConnection`
        TERMINAL_LOG.error("Exception while executing command: {}", commandContext.getInput(), e);
        commandContext.getEmbed()
            .title("Command Execution Error")
            .description(e.getClass().getName() + "\n\n" + e.getMessage() + "\n\nFull stack trace in logs.")
            .errorColor();
    }
}
