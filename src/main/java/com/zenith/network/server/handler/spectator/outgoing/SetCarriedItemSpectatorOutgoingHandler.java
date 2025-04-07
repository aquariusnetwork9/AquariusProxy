package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetCarriedItemPacket;

public class SetCarriedItemSpectatorOutgoingHandler implements PacketHandler<ClientboundSetCarriedItemPacket, ServerSession> {
    @Override
    public ClientboundSetCarriedItemPacket apply(ClientboundSetCarriedItemPacket packet, ServerSession session) {
        return null;
    }
}
