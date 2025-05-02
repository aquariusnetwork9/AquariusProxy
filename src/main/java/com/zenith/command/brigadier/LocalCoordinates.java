package com.zenith.command.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zenith.command.api.CommandContext;
import com.zenith.feature.pathfinder.PlayerContext;
import lombok.Data;
import org.cloudburstmc.math.vector.Vector2d;
import org.cloudburstmc.math.vector.Vector3d;

import static com.zenith.Globals.CACHE;

@Data
public class LocalCoordinates implements Coordinates {
    public static final char PREFIX_LOCAL_COORDINATE = '^';
    private final double left;
    private final double up;
    private final double forwards;

    @Override
    public Vector3d getPosition(CommandContext source) {
        var srcRot = Vector2d.from(CACHE.getPlayerCache().getYaw(), CACHE.getPlayerCache().getPitch());
        Vector3d srcPos = PlayerContext.INSTANCE.playerHead();
        float f = (float) Math.cos((srcRot.getY() + 90.0) * (Math.PI / 180.0));
        float g = (float) Math.sin((srcRot.getY() + 90.0) * (Math.PI / 180.0));
        float h = (float) Math.cos(-srcRot.getX() * (Math.PI / 180.0));
        float i = (float) Math.sin(-srcRot.getX() * (Math.PI / 180.0));
        float j = (float) Math.cos((-srcRot.getX() + 90.0) * (Math.PI / 180.0));
        float k = (float) Math.sin((-srcRot.getX() + 90.0) * (Math.PI / 180.0));
        Vector3d vec32 = Vector3d.from(f * h, i, (double)(g * h));
        Vector3d vec33 = Vector3d.from(f * j, k, (double)(g * j));
        Vector3d vec34 = vec32.cross(vec33).mul(-1.0);
        double d = vec32.getX() * this.forwards + vec33.getX() * this.up + vec34.getX() * this.left;
        double e = vec32.getY() * this.forwards + vec33.getY() * this.up + vec34.getY() * this.left;
        double l = vec32.getZ() * this.forwards + vec33.getZ() * this.up + vec34.getZ() * this.left;
        return Vector3d.from(srcPos.getX() + d, srcPos.getY() + e, srcPos.getZ() + l);
    }

    @Override
    public Vector2d getRotation(CommandContext source) {
        return Vector2d.ZERO;
    }

    @Override
    public boolean isXRelative() {
        return true;
    }

    @Override
    public boolean isYRelative() {
        return true;
    }

    @Override
    public boolean isZRelative() {
        return true;
    }

    public static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        double d = readDouble(reader, i);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            double e = readDouble(reader, i);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double f = readDouble(reader, i);
                return new LocalCoordinates(d, e, f);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    private static double readDouble(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else if (reader.peek() != '^') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else {
            reader.skip();
            return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        }
    }
}
