package com.zenith.command.api;

@FunctionalInterface
public interface CommandSuccessHandler {
    void handle(CommandContext context);
}
