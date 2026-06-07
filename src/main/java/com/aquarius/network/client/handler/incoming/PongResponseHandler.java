package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.status.clientbound.ClientboundPongResponsePacket;

public class PongResponseHandler implements PacketHandler<ClientboundPongResponsePacket, ClientSession> {
    public static final PongResponseHandler INSTANCE = new PongResponseHandler();
    @Override
    public ClientboundPongResponsePacket apply(final ClientboundPongResponsePacket packet, final ClientSession session) {
        if (session.getLastPingId() == packet.getPingTime()) {
            // this is from our own ping task
            session.setPing(System.currentTimeMillis() - session.getLastPingSentTime());
            return null;
        }
        return packet;
    }
}
