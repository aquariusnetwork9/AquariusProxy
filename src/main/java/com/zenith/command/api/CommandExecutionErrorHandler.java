package com.zenith.command.api;

@FunctionalInterface
public interface CommandExecutionErrorHandler {
    void handle(CommandContext context);
}
