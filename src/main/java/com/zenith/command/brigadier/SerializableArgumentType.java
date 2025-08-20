package com.zenith.command.brigadier;

import com.mojang.brigadier.arguments.ArgumentType;

public interface SerializableArgumentType<T> extends ArgumentType<T> {
    ArgumentSerializerProperties serializerProperties();
}
