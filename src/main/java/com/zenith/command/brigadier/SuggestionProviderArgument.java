package com.zenith.command.brigadier;

public interface SuggestionProviderArgument {
    default String suggestionType() {
        return "minecraft:ask_server";
    }
}
