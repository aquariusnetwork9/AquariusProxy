package com.zenith.mc.block;

public record FluidState(boolean water, boolean source, int amount, boolean falling) {
    public boolean lava() {
        return !water;
    }
}
