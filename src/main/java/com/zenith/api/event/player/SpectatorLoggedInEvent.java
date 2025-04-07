package com.zenith.api.event.player;

import com.zenith.api.network.server.ServerSession;

// the spectator has logged in and been sent the ClientboundLoginPacket
public record SpectatorLoggedInEvent(ServerSession session) { }
