package com.zenith.event.update;

import java.util.Optional;

public record UpdateStartEvent(Optional<String> newVersion) { }
