package com.zenith.api.event.player;

import com.zenith.api.network.server.ServerSession;

public record PlayerConnectionRemovedEvent(ServerSession serverConnection) { }
