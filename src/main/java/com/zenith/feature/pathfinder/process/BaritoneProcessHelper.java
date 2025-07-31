package com.zenith.feature.pathfinder.process;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PlayerContext;

public abstract class BaritoneProcessHelper implements IBaritoneProcess, ProcessUtil {

    protected final Baritone baritone;
    protected final PlayerContext ctx;

    public BaritoneProcessHelper(final Baritone baritone) {
        this.baritone = baritone;
        this.ctx = this.baritone.getPlayerContext();
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public void stop() {
        onLostControl();
    }
}
