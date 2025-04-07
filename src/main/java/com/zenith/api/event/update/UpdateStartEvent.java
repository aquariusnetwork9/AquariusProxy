package com.zenith.api.event.update;

import java.util.Optional;

public record UpdateStartEvent(Optional<String> newVersion) { }
