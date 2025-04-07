package com.zenith.api.event.player;

import com.zenith.network.server.ServerSession;

public record PlayerConnectionRemovedEvent(ServerSession serverConnection) { }
