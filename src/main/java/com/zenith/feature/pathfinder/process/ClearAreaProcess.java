package com.zenith.feature.pathfinder.process;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PathingCommand;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.mc.block.BlockPos;

public class ClearAreaProcess extends BaritoneProcessHelper {
    BlockPos pos1, pos2;

    public ClearAreaProcess(final Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return pos1 != null && pos2 != null;
    }

    @Override
    public PathingCommand onTick(final boolean calcFailed, final boolean isSafeToCancel, final Goal currentGoal, final PathingCommand prevCommand) {
        return null;
    }

    @Override
    public void onLostControl() {
        pos1 = pos2 = null;
    }

    @Override
    public String displayName0() {
        return "Clear Area";
    }
}
