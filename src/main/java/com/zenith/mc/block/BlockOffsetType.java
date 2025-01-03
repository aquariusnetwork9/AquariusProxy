package com.zenith.mc.block;

import com.zenith.util.math.MathHelper;
import com.zenith.util.math.MutableVec3d;
import lombok.Getter;

@Getter
public enum BlockOffsetType {
    NONE((block, x, y, z) -> MutableVec3d.ZERO),
    XZ((block, x, y, z) -> {
        long seed = MathHelper.getSeed(x, 0, z);
        float maxHorizontalOffset = block.maxHorizontalOffset();
        double xOffset = MathHelper.clamp(((double)((float)(seed & 15L) / 15.0F) - 0.5) * 0.5, -maxHorizontalOffset, maxHorizontalOffset);
        double zOffset = MathHelper.clamp(((double)((float)(seed >> 8 & 15L) / 15.0F) - 0.5) * 0.5, -maxHorizontalOffset, maxHorizontalOffset);
        return new MutableVec3d(xOffset, 0.0, zOffset);
    }),
    XYZ((block, x, y, z) -> {
        long seed = MathHelper.getSeed(x, 0, z);
        double yOffset = ((double)((float)(seed >> 4 & 15L) / 15.0F) - 1.0) * (double)block.maxVerticalOffset();
        float maxHorizontalOffset = block.maxHorizontalOffset();
        double xOffset = MathHelper.clamp(((double)((float)(seed & 15L) / 15.0F) - 0.5) * 0.5, -maxHorizontalOffset, maxHorizontalOffset);
        double zOffset = MathHelper.clamp(((double)((float)(seed >> 8 & 15L) / 15.0F) - 0.5) * 0.5, -maxHorizontalOffset, maxHorizontalOffset);
        return new MutableVec3d(xOffset, yOffset, zOffset);
    });
    private final OffsetFunction offsetFunction;

    BlockOffsetType(OffsetFunction offsetFunction) {
        this.offsetFunction = offsetFunction;
    }

    @FunctionalInterface
    public interface OffsetFunction {
        MutableVec3d offset(Block block, int x, int y, int z);
    }
}
