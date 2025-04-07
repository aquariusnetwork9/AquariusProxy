package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetDataPacket;

public class ContainerSetDataSpectatorOutgoingHandler implements PacketHandler<ClientboundContainerSetDataPacket, ServerSession> {
    @Override
    public ClientboundContainerSetDataPacket apply(ClientboundContainerSetDataPacket packet, ServerSession session) {
        return null;
    }
}
