package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum TrialSpawnerState implements StringRepresentable {
    INACTIVE("inactive"),
    WAITING_FOR_PLAYERS("waiting_for_players"),
    ACTIVE("active"),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection"),
    EJECTING_REWARD("ejecting_reward"),
    COOLDOWN("cooldown");

    private final String name;

    TrialSpawnerState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
