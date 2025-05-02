package com.zenith.command.brigadier;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zenith.util.ComponentSerializer;
import lombok.Data;
import net.kyori.adventure.text.Component;

@Data
public class WorldCoordinate {
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.missing.double"))));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(
        new LiteralMessage(ComponentSerializer.serializePlain(Component.translatable("argument.pos.missing.int"))));
    private final boolean relative;
    private final double value;

    public double get(double coord) {
        return this.relative ? this.value + coord : this.value;
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else {
            boolean rel = isRelative(reader);
            int i = reader.getCursor();
            double d = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
            String string = reader.getString().substring(i, reader.getCursor());
            if (rel && string.isEmpty()) {
                return new WorldCoordinate(true, 0.0);
            } else {
                if (!string.contains(".") && !rel && centerCorrect) {
                    d += 0.5;
                }

                return new WorldCoordinate(rel, d);
            }
        }
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        } else {
            boolean rel = isRelative(reader);
            double d;
            if (reader.canRead() && reader.peek() != ' ') {
                d = rel ? reader.readDouble() : (double)reader.readInt();
            } else {
                d = 0.0;
            }

            return new WorldCoordinate(rel, d);
        }
    }

    public static boolean isRelative(StringReader reader) {
        boolean rel;
        if (reader.peek() == '~') {
            rel = true;
            reader.skip();
        } else {
            rel = false;
        }
        return rel;
    }

}
