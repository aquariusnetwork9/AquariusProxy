package com.zenith.feature.pathfinder.calc;

import com.zenith.feature.pathfinder.goals.Goal;

public abstract class PathBase implements IPath {
    @Override
    public PathBase cutoffAtLoadedChunks() { // <-- cursed cursed cursed
        return this;
//        if (!Baritone.settings().cutoffAtLoadBoundary.value) {
//            return this;
//        }
//        BlockStateInterface bsi = (BlockStateInterface) bsi0;
//        for (int i = 0; i < positions().size(); i++) {
//            BlockPos pos = positions().get(i);
//            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
//                return new CutoffPath(this, i);
//            }
//        }
//        return this;
    }

    @Override
    public PathBase staticCutoff(Goal destination) {
        int min = 30;
        if (length() < min) {
            return this;
        }
        if (destination == null || destination.isInGoal(getDest())) {
            return this;
        }
        double factor = 0.9;
        int newLength = (int) ((length() - min) * factor) + min - 1;
        return new CutoffPath(this, newLength);
    }
}
