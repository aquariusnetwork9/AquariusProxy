package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;

import static com.aquarius.Globals.CONFIG;

public class CLoginCompressionHandler implements PacketHandler<ClientboundLoginCompressionPacket, ClientSession> {
    @Override
    public ClientboundLoginCompressionPacket apply(final ClientboundLoginCompressionPacket packet, final ClientSession session) {
        session.setCompressionThreshold(packet.getThreshold(), CONFIG.client.compressionLevel, false);
        return null;
    }
}
