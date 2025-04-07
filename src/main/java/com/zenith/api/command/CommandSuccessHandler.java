package com.zenith.api.command;

@FunctionalInterface
public interface CommandSuccessHandler {
    void handle(CommandContext context);
}
