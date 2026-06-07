package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;

public class CDisconnectHandler implements PacketHandler<ClientboundDisconnectPacket, ClientSession> {
    public static final CDisconnectHandler INSTANCE = new CDisconnectHandler();
    @Override
    public ClientboundDisconnectPacket apply(final ClientboundDisconnectPacket packet, final ClientSession session) {
        session.disconnect(packet.getReason());
        return null;
    }
}
