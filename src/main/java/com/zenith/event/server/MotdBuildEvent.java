package com.zenith.event.server;

import net.kyori.adventure.text.Component;

public class MotdBuildEvent {
    private Component motd;

    public Component getMotd() {
        return motd;
    }

    public void setMotd(final Component motd) {
        this.motd = motd;
    }
}
