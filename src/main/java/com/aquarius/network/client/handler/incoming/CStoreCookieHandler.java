package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;

public class CStoreCookieHandler implements PacketHandler<ClientboundStoreCookiePacket, ClientSession> {
    @Override
    public ClientboundStoreCookiePacket apply(final ClientboundStoreCookiePacket packet, final ClientSession session) {
        // todo: store the cookie?
        return null;
    }
}
