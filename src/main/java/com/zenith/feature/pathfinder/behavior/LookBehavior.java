package com.zenith.feature.pathfinder.behavior;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.player.Rotation;
import org.jspecify.annotations.Nullable;

public class LookBehavior extends Behavior {
    /**
     * The current look target, may be {@code null}.
     */
    @Nullable private Rotation targetRotation = null;
    @Nullable public Rotation currentRotation = null;

    public LookBehavior(Baritone baritone) {
        super(baritone);
    }

    public void updateRotation(Rotation rotation) {
        this.targetRotation = rotation;
    }

    public void onTick() {
        if (this.targetRotation == null) {
            this.currentRotation = null;
            return;
        }
        this.currentRotation = this.targetRotation;
        this.targetRotation = null;
    }
}
