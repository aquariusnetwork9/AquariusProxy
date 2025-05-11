package com.zenith.mc.block;

import com.zenith.util.math.MathHelper;
import org.cloudburstmc.math.vector.Vector3i;
import org.jspecify.annotations.NonNull;

public record BlockPos(int x, int y, int z) implements Comparable<BlockPos> {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(double d, double e, double f) {
        this(MathHelper.floorI(d), MathHelper.floorI(e), MathHelper.floorI(f));
    }

    public BlockPos(final BlockPos other) {
        this(other.x(), other.y(), other.z());
    }

    public int getChunkX() {
        return x >> 4;
    }

    public int getChunkY() {
        return y >> 4;
    }

    public int getChunkZ() {
        return z >> 4;
    }

    public BlockPos addX(int delta) {
        return new BlockPos(x + delta, y, z);
    }

    public BlockPos add(final int x, final int y, final int z) {
        return new BlockPos(x() + x, y() + y, z() + z);
    }

    public BlockPos addY(int delta) {
        return new BlockPos(x, y + delta, z);
    }

    public BlockPos addZ(int delta) {
        return new BlockPos(x, y, z + delta);
    }

    public BlockPos minus(BlockPos other) {
        return new BlockPos(x - other.x(), y - other.y(), z - other.z());
    }

    public BlockPos north() {
        return this.add(0, 0, -1);
    }

    public BlockPos north(int n) {
        return this.add(0, 0, -n);
    }

    public BlockPos south() {
        return this.add(0, 0, 1);
    }

    public BlockPos south(int n) {
        return this.add(0, 0, n);
    }

    public BlockPos east() {
        return this.add(1, 0, 0);
    }

    public BlockPos east(int n) {
        return this.add(n, 0, 0);
    }

    public BlockPos west() {
        return this.add(-1, 0, 0);
    }

    public BlockPos west(int n) {
        return this.add(-n, 0, 0);
    }

    public BlockPos above() {
        return this.add(0, 1, 0);
    }

    public BlockPos above(int n) {
        return this.add(0, n, 0);
    }

    public BlockPos below() {
        return this.add(0, -1, 0);
    }

    public BlockPos below(int n) {
        return this.add(0, -n, 0);
    }

    public BlockPos relative(Direction direction) {
        return this.add(direction.x(), direction.y(), direction.z());
    }

    public BlockPos offset(BlockPos other) {
        if (other.x() == 0 && other.y() == 0 && other.z() == 0) {
            return this;
        }
        return add(other.x(), other.y(), other.z());
    }

    public BlockPos subtract(BlockPos other) {
        if (other.x() == 0 && other.y() == 0 && other.z() == 0) {
            return this;
        }
        return add(-other.x(), -other.y(), -other.z());
    }

    public BlockPos relative(Direction direction, int n) {
        if (n == 0) {
            return this;
        }
        return this.add(direction.x() * n, direction.y() * n, direction.z() * n);
    }

    public double distance(final BlockPos other) {
        return Math.sqrt(Math.pow(other.x - x, 2) + Math.pow(other.y - y, 2) + Math.pow(other.z - z, 2));
    }

    public double squaredDistance(final BlockPos other) {
        return Math.pow(other.x - x, 2) + Math.pow(other.y - y, 2) + Math.pow(other.z - z, 2);
    }

    public double squaredDistance(final double x, final double y, final double z) {
        double d = (double) this.x() + 0.5 - x;
        double e = (double) this.y() + 0.5 - y;
        double f = (double) this.z() + 0.5 - z;
        return d * d + e * e + f * f;
    }

    public Vector3i directionTo(BlockPos other) {
        double dx = other.x() - x;
        double dy = other.y() - y;
        double dz = other.z() - z;
        int xDir = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
        int yDir = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
        int zDir = dz == 0 ? 0 : (dz > 0 ? 1 : -1);
        return Vector3i.from(xDir, yDir, zDir);
    }

    private static final int PACKED_X_LENGTH = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
    private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
    public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
    private static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

    public long asLong() {
        return asLong(this.x(), this.y(), this.z());
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        l |= ((long) y & PACKED_Y_MASK);
        return l | ((long) z & PACKED_Z_MASK) << Z_OFFSET;
    }

    public static int getX(long packedPos) {
        return (int) (packedPos << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    public static int getY(long packedPos) {
        return (int) (packedPos << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    public static int getZ(long packedPos) {
        return (int) (packedPos << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }

    public static int compare(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (y1 == y2) {
            return z1 == z2 ? x1 - x2 : z1 - z2;
        } else {
            return y1 - y2;
        }
    }

    public static BlockPos fromLong(long l) {
        return new BlockPos(getX(l), getY(l), getZ(l));
    }

    @Override
    public int compareTo(@NonNull final BlockPos o) {
        if (this.y() == o.y()) {
            return this.z() == o.z() ? this.x() - o.x() : this.z() - o.z();
        } else {
            return this.y() - o.y();
        }
    }

    @Override
    public int hashCode() {
        return (int) longHash(x, y, z);
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }

    public static long longHash(BlockPos pos) {
        return longHash(pos.x, pos.y, pos.z);
    }

    public static long longHash(int x, int y, int z) {
        // TODO use the same thing as BlockPos.fromLong();
        // invertibility would be incredibly useful
        /*
         *   This is the hashcode implementation of Vec3i (the superclass of the class which I shall not name)
         *
         *   public int hashCode() {
         *       return (this.getY() + this.getZ() * 31) * 31 + this.getX();
         *   }
         *
         *   That is terrible and has tons of collisions and makes the HashMap terribly inefficient.
         *
         *   That's why we grab out the X, Y, Z and calculate our own hashcode
         */
        long hash = 3241;
        hash = 3457689L * hash + x;
        hash = 8734625L * hash + y;
        hash = 2873465L * hash + z;
        return hash;
    }

    public BlockPos cross(final BlockPos other) {
        return new BlockPos(this.y() * other.z() - this.z() * other.y(), this.z() * other.x() - this.x() * other.z(), this.x() * other.y() - this.y() * other.x());
    }

    public BlockPos offset(final int x, final int y, final int z) {
        return new BlockPos(this.x() + x, this.y() + y, this.z() + z);
    }
}
