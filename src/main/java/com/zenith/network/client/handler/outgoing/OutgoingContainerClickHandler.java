package com.zenith.network.client.handler.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import static com.zenith.Globals.CACHE;

public class OutgoingContainerClickHandler implements PacketHandler<ServerboundContainerClickPacket, ClientSession> {
    @Override
    public ServerboundContainerClickPacket apply(final ServerboundContainerClickPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getActionId().set(packet.getStateId());
        return packet;
    }
}
