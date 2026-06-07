package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCookieRequestPacket;

public class CCookieRequestHandler implements PacketHandler<ClientboundCookieRequestPacket, ClientSession> {
    @Override
    public ClientboundCookieRequestPacket apply(final ClientboundCookieRequestPacket packet, final ClientSession session) {
        return null;
    }
}
