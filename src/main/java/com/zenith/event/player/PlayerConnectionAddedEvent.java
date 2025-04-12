package com.zenith.event.player;

import com.zenith.network.server.ServerSession;

public record PlayerConnectionAddedEvent(ServerSession serverConnection) { }
