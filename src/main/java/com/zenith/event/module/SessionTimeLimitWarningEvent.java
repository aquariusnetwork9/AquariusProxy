package com.zenith.event.module;

import java.time.Duration;

public record SessionTimeLimitWarningEvent(
    Duration sessionTimeLimit,
    Duration durationUntilKick
) { }
