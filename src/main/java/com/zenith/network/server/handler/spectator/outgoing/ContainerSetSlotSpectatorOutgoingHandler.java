package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

public class ContainerSetSlotSpectatorOutgoingHandler implements PacketHandler<ClientboundContainerSetSlotPacket, ServerSession> {
    @Override
    public ClientboundContainerSetSlotPacket apply(ClientboundContainerSetSlotPacket packet, ServerSession session) {
        return null;
    }
}
