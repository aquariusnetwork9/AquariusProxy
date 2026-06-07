package com.aquarius.network.server.handler.shared.postoutgoing;

import com.aquarius.network.codec.PostOutgoingPacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;

public class PingPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundPingPacket, ServerSession> {
    @Override
    public void accept(final ClientboundPingPacket packet, final ServerSession session) {
        session.setLastPingId(packet.getId());
        session.setLastPingTime(System.currentTimeMillis());
    }
}
