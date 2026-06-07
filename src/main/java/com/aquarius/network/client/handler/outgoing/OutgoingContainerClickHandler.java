package com.aquarius.network.client.handler.outgoing;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import static com.aquarius.Globals.CACHE;

public class OutgoingContainerClickHandler implements PacketHandler<ServerboundContainerClickPacket, ClientSession> {
    @Override
    public ServerboundContainerClickPacket apply(final ServerboundContainerClickPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getActionId().set(packet.getStateId());
        return packet;
    }
}
