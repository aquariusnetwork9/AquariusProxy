package com.zenith.network.server.handler.shared.postoutgoing;

import com.zenith.api.network.PostOutgoingPacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;

public class PingPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundPingPacket, ServerSession> {
    @Override
    public void accept(final ClientboundPingPacket packet, final ServerSession session) {
        session.setLastPingId(packet.getId());
        session.setLastPingTime(System.currentTimeMillis());
    }
}
