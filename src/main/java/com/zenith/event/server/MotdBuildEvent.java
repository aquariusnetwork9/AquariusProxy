package com.zenith.event.server;

import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;

import java.util.Objects;

@AllArgsConstructor
public class MotdBuildEvent {
    private Component motd;

    public Component getMotd() {
        return motd;
    }

    public void setMotd(final Component motd) {
        Objects.requireNonNull(motd, "motd cannot be null");
        this.motd = motd;
    }
}
