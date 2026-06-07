package com.aquarius.feature.tasks;

import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandOutputHelper;
import com.aquarius.command.api.CommandSource;
import com.aquarius.discord.Embed;
import com.aquarius.event.module.TasksCommandExecutedEvent;
import lombok.Data;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

import static com.aquarius.Globals.*;

/**
 * An {@link Action} that executes a command.
 */
@Data
@ApiStatus.Experimental
public class CommandAction implements Action {
    private final String command;

    static final CommandSource SOURCE = new CommandActionSource();

    public CommandAction(String command) {
        this.command = command;
    }

    @Override
    public void execute() {
        MODULE_LOG.info("Executing scheduled command: {}", command);
        EVENT_BUS.postAsync(new TasksCommandExecutedEvent(command));
        var ctx = CommandContext.create(command, SOURCE);
        COMMAND.execute(ctx);
        if (!ctx.isNoOutput() && !ctx.getEmbed().isTitlePresent() && ctx.getMultiLineOutput().isEmpty()) {
            CommandOutputHelper.logEmbedOutputToDiscord(Embed.builder()
                .title("Tasks Error")
                .addField("Error", "Unknown Command")
                .addField("Command", "`" + command + "`"));
            return;
        }
        if (CONFIG.client.extra.tasks.logCommandActionOutput) {
            CommandOutputHelper.logEmbedOutputToDiscord(ctx.getEmbed());
            CommandOutputHelper.logMultiLineOutputToDiscord(ctx.getMultiLineOutput());
        }
    }

    public static class CommandActionSource implements CommandSource {
        @Override
        public String name() {
            return "Tasks";
        }

        @Override
        public boolean validateAccountOwner(final CommandContext ctx) {
            return false;
        }

        @Override
        public void logEmbed(final CommandContext ctx, final Embed embed) {
            if (CONFIG.client.extra.tasks.logCommandActionOutput) {
                CommandOutputHelper.logEmbedOutputToDiscord(embed);
            }
        }

        @Override
        public void logMultiLine(final List<String> multiLine) {
            if (CONFIG.client.extra.tasks.logCommandActionOutput) {
                CommandOutputHelper.logMultiLineOutputToDiscord(multiLine);
            }
        }
    }
}
