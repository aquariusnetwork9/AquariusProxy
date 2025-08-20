package com.zenith.command.brigadier;

import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ArgumentSerializerProperties(
    @NonNull CommandParser commandParser,
    @Nullable CommandProperties commandProperties
) { }
