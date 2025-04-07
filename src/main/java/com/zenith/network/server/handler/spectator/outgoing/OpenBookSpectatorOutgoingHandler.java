package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenBookPacket;

public class OpenBookSpectatorOutgoingHandler implements PacketHandler<ClientboundOpenBookPacket, ServerSession> {
    @Override
    public ClientboundOpenBookPacket apply(ClientboundOpenBookPacket packet, ServerSession session) {
        return null;
    }
}
