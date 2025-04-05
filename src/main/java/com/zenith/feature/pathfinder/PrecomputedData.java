package com.zenith.feature.pathfinder;

import com.zenith.feature.pathfinder.movement.MovementHelper;
import com.zenith.mc.block.BlockRegistry;

public class PrecomputedData {
    private final byte[] data = new byte[BlockRegistry.REGISTRY.get(BlockRegistry.REGISTRY.size()-1).maxStateId()];
    public static final PrecomputedData INSTANCE = new PrecomputedData();

    private PrecomputedData() {}

    /**
     * byte layout:
     *          0              1              2              3              4              5              6              7
     *          |              |             |              |              |              |              |              |
     *      unused         unused        canWalkOn        maybe    canWalkThrough       maybe        fullyPassable    completed
     */

    private static final byte COMPLETED_MASK = (byte) 0x01;
    private static final byte CAN_WALK_ON_MASK = (byte) 0x20;
    private static final byte CAN_WALK_ON_MAYBE_MASK = (byte) 0x10;
    private static final byte CAN_WALK_THROUGH_MASK = (byte) 0x08;
    private static final byte CAN_WALK_THROUGH_MAYBE_MASK = (byte) 0x04;
    private static final byte FULLY_PASSABLE_MASK = (byte) 0x02;

    private static final byte zero = 0;

    private byte fillData(int id, int blockStateId) {
        byte blockData = zero;

        Ternary canWalkOnState = MovementHelper.canWalkOnBlockState(blockStateId);
        switch (canWalkOnState) {
            case YES -> blockData |= CAN_WALK_ON_MASK;
            case MAYBE -> blockData |= CAN_WALK_ON_MAYBE_MASK;
        }

        Ternary canWalkThroughState = MovementHelper.canWalkThroughBlockState(blockStateId);
        switch (canWalkThroughState) {
            case YES -> blockData |= CAN_WALK_THROUGH_MASK;
            case MAYBE -> blockData |= CAN_WALK_THROUGH_MAYBE_MASK;
        }

        boolean fullyPassableState = MovementHelper.fullyPassableBlockState(blockStateId);
        if (fullyPassableState) {
            blockData |= FULLY_PASSABLE_MASK;
        }

        blockData |= COMPLETED_MASK;

        data[id] = blockData;
        return blockData;
    }

    public boolean canWalkOn(int x, int y, int z, int blockStateId) {
        byte blockData = data[blockStateId];

        if ((blockData & COMPLETED_MASK) == zero) { // we need to fill in the data
            blockData = fillData(blockStateId, blockStateId);
        }

        if ((blockData & CAN_WALK_ON_MAYBE_MASK) != zero) {
            return MovementHelper.canWalkOnPosition(x, y, z, blockStateId);
        } else {
            return (blockData & CAN_WALK_ON_MASK) != zero;
        }
    }

    public boolean canWalkThrough(int x, int y, int z, int blockStateId) {
        byte blockData = data[blockStateId];

        if ((blockData & COMPLETED_MASK) == zero) { // we need to fill in the data
            blockData = fillData(blockStateId, blockStateId);
        }

        if ((blockData & CAN_WALK_THROUGH_MAYBE_MASK) != zero) {
            return MovementHelper.canWalkThroughPosition(x, y, z, blockStateId);
        } else {
            return (blockData & CAN_WALK_THROUGH_MASK) != zero;
        }
    }

    public boolean fullyPassable(int blockStateId) {
        byte blockData = data[blockStateId];

        if ((blockData & COMPLETED_MASK) == zero) { // we need to fill in the data
            blockData = fillData(blockStateId, blockStateId);
        }

        return (blockData & FULLY_PASSABLE_MASK) != zero;
    }
}
