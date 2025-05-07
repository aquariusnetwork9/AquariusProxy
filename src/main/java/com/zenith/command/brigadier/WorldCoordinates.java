package com.zenith.command.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zenith.command.api.CommandContext;
import lombok.Data;
import org.cloudburstmc.math.vector.Vector2d;
import org.cloudburstmc.math.vector.Vector3d;

import static com.zenith.Globals.CACHE;

@Data
public class WorldCoordinates implements Coordinates {
    private final WorldCoordinate x;
    private final WorldCoordinate y;
    private final WorldCoordinate z;

    @Override
    public Vector3d getPosition(CommandContext source) {
        Vector3d srcPos = CACHE.getPlayerCache().getThePlayer().position();
        return Vector3d.from(this.x.get(srcPos.getX()), this.y.get(srcPos.getY()), this.z.get(srcPos.getZ()));
    }

    @Override
    public Vector2d getRotation(CommandContext source) {
        var srcRot = Vector2d.from(CACHE.getPlayerCache().getYaw(), CACHE.getPlayerCache().getPitch());
        return Vector2d.from(this.x.get(srcRot.getX()), this.y.get(srcRot.getY()));
    }

    @Override
    public boolean isXRelative() {
        return this.x.isRelative();
    }

    @Override
    public boolean isYRelative() {
        return this.y.isRelative();
    }

    @Override
    public boolean isZRelative() {
        return this.z.isRelative();
    }

    public static WorldCoordinates parseInt(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate2 = WorldCoordinate.parseInt(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate3 = WorldCoordinate.parseInt(reader);
                return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        int i = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, centerCorrect);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate3 = WorldCoordinate.parseDouble(reader, centerCorrect);
                return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates absolute(double x, double y, double z) {
        return new WorldCoordinates(new WorldCoordinate(false, x), new WorldCoordinate(false, y), new WorldCoordinate(false, z));
    }

    public static WorldCoordinates absolute(Vector2d vec2) {
        return new WorldCoordinates(new WorldCoordinate(false, vec2.getX()), new WorldCoordinate(false, vec2.getY()), new WorldCoordinate(true, 0.0));
    }

    public static WorldCoordinates current() {
        return new WorldCoordinates(new WorldCoordinate(true, 0.0), new WorldCoordinate(true, 0.0), new WorldCoordinate(true, 0.0));
    }
}
