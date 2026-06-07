package com.aquarius.feature.pathfinder.behavior;

import com.aquarius.feature.pathfinder.Baritone;
import com.aquarius.feature.pathfinder.PlayerContext;

public class Behavior {
    public final Baritone baritone;
    public final PlayerContext ctx;

    protected Behavior(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = PlayerContext.INSTANCE;
    }
}
