package com.aquarius.command.api;

@FunctionalInterface
public interface CommandExecutionErrorHandler {
    void handle(CommandContext context);
}
