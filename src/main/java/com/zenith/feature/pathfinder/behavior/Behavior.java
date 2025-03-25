package com.zenith.feature.pathfinder.behavior;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PlayerContext;

public class Behavior {
    public final Baritone baritone;
    public final PlayerContext ctx;

    protected Behavior(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = PlayerContext.INSTANCE;
    }
}
