package com.zenith.event.update;

import java.util.Optional;

public record UpdateAvailableEvent(String version) {
    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }
}
