package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;

public class ContainerCloseSpectatorOutgoingHandler implements PacketHandler<ClientboundContainerClosePacket, ServerSession> {
    @Override
    public ClientboundContainerClosePacket apply(ClientboundContainerClosePacket packet, ServerSession session) {
        return null;
    }
}
