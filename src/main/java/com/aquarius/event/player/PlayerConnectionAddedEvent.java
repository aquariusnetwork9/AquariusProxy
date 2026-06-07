package com.aquarius.event.player;

import com.aquarius.network.server.ServerSession;

public record PlayerConnectionAddedEvent(ServerSession serverConnection) { }
