package com.aquarius.command.api;

@FunctionalInterface
public interface CommandSuccessHandler {
    void handle(CommandContext context);
}
