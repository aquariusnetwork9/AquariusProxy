package com.zenith.event.server;

import lombok.AllArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
public class ServerIconBuildEvent {
    private byte[] icon;

    public byte[] getIcon() {
        return icon;
    }

    public void setIcon(final byte[] icon) {
        Objects.requireNonNull(icon, "icon cannot be null");
        this.icon = icon;
    }
}
