package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;

public class CStoreCookieHandler implements PacketHandler<ClientboundStoreCookiePacket, ClientSession> {
    @Override
    public ClientboundStoreCookiePacket apply(final ClientboundStoreCookiePacket packet, final ClientSession session) {
        // todo: store the cookie?
        return null;
    }
}
