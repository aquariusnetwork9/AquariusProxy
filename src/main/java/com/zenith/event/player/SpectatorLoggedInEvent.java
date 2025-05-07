package com.zenith.event.player;

import com.zenith.network.server.ServerSession;

// the spectator has logged in and been sent the ClientboundLoginPacket
public record SpectatorLoggedInEvent(ServerSession session) { }
