package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum VaultState implements StringRepresentable {
    INACTIVE("inactive"),
    ACTIVE("active"),
    UNLOCKING("unlocking"),
    EJECTING("ejecting");

    private final String stateName;

    VaultState(String stateName) {
        this.stateName = stateName;
    }

    @Override
    public String getSerializedName() {
        return stateName;
    }
}
