package com.zenith.event.server;

import net.kyori.adventure.text.Component;

import java.util.Objects;

public class CustomTablistFooterBuildEvent {
    private Component footerComponent;

    public CustomTablistFooterBuildEvent(Component footerComponent) {
        this.footerComponent = footerComponent;
    }

    public Component getFooterComponent() {
        return footerComponent;
    }

    public void setFooterComponent(Component footerComponent) {
        Objects.requireNonNull(footerComponent, "Footer component cannot be null");
        this.footerComponent = footerComponent;
    }
}
