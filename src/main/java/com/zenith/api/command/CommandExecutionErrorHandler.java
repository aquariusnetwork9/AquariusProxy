package com.zenith.api.command;

@FunctionalInterface
public interface CommandExecutionErrorHandler {
    void handle(CommandContext context);
}
