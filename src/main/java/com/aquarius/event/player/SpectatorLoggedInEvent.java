package com.aquarius.event.player;

import com.aquarius.network.server.ServerSession;

// the spectator has logged in and been sent the ClientboundLoginPacket
public record SpectatorLoggedInEvent(ServerSession session) { }
