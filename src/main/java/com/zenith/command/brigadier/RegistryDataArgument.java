package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.mc.Registry;
import com.zenith.mc.RegistryData;
import lombok.Data;
import net.kyori.adventure.key.Key;

@Data
public class RegistryDataArgument<T extends RegistryData> implements ArgumentType<RegistryData> {
    public static final SimpleCommandExceptionType INVALID_REGISTRY_KEY = new SimpleCommandExceptionType(
        new LiteralMessage("Invalid registry key"));

    private final Key registryKey;
    private final Registry<T> registry;

    public static <T extends RegistryData> RegistryDataArgument<T> key(String registryKey, Registry<T> registry) {
        return new RegistryDataArgument<>(Key.key(registryKey), registry);
    }

    public static <T extends RegistryData> T getRegistryData(final com.mojang.brigadier.context.CommandContext<com.zenith.command.api.CommandContext> context, String name) {
        return (T) context.getArgument(name, RegistryData.class);
    }

    @Override
    public T parse(final StringReader reader) throws CommandSyntaxException {
        var key = ResourceLocationArgument.read(reader);
        var data = registry.get(key.value());
        if (data == null) {
            throw INVALID_REGISTRY_KEY.create();
        }
        return data;
    }
}
