package com.aquarius.command.api;

import com.mojang.brigadier.context.CommandContext;

@FunctionalInterface
public interface IExecutes<S> {
    void execute(CommandContext<S> context);
}
