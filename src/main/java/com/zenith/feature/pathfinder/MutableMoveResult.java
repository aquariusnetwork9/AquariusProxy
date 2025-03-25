package com.zenith.feature.pathfinder;

import com.zenith.feature.pathfinder.movement.ActionCosts;

public class MutableMoveResult {
    public int x;
    public int y;
    public int z;
    public double cost;

    public MutableMoveResult() {
        reset();
    }

    public final void reset() {
        x = 0;
        y = 0;
        z = 0;
        cost = ActionCosts.COST_INF;
    }
}
