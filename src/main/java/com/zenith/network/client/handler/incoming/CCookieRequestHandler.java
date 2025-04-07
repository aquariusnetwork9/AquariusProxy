package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCookieRequestPacket;

public class CCookieRequestHandler implements PacketHandler<ClientboundCookieRequestPacket, ClientSession> {
    @Override
    public ClientboundCookieRequestPacket apply(final ClientboundCookieRequestPacket packet, final ClientSession session) {
        return null;
    }
}
