package com.zenith.command.api;

@FunctionalInterface
public interface CommandExecutionExceptionHandler {
    void handle(CommandContext context, Throwable exception);
}
