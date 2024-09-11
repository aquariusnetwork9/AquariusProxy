package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundTagQueryPacket;

public class COTagQueryHandler implements PacketHandler<ClientboundTagQueryPacket, ServerSession> {
    @Override
    public ClientboundTagQueryPacket apply(final ClientboundTagQueryPacket packet, final ServerSession session) {
        return null;
    }
}
