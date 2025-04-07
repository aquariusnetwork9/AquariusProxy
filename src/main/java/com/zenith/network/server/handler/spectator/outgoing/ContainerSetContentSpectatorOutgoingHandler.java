package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;

public class ContainerSetContentSpectatorOutgoingHandler implements PacketHandler<ClientboundContainerSetContentPacket, ServerSession> {
    @Override
    public ClientboundContainerSetContentPacket apply(ClientboundContainerSetContentPacket packet, ServerSession session) {
        return null;
    }
}
