package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundTagQueryPacket;

public class COTagQueryHandler implements PacketHandler<ClientboundTagQueryPacket, ServerSession> {
    @Override
    public ClientboundTagQueryPacket apply(final ClientboundTagQueryPacket packet, final ServerSession session) {
        return null;
    }
}
