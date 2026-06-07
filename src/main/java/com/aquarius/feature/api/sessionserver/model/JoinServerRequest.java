package com.aquarius.feature.api.sessionserver.model;

import java.util.UUID;

public record JoinServerRequest(
    UUID selectedProfile, // UUID without dashes
    String accessToken,
    String serverId
) { }
