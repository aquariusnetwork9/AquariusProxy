package com.aquarius.network.server.handler.shared.postoutgoing;

import com.aquarius.network.codec.PostOutgoingPacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;

import static com.aquarius.Globals.CONFIG;

public class LoginCompressionPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundLoginCompressionPacket, ServerSession> {
    @Override
    public void accept(final ClientboundLoginCompressionPacket packet, final ServerSession session) {
        session.setCompressionThreshold(packet.getThreshold(), CONFIG.server.compressionLevel, true);
        var profile = session.getProfileCache().getProfile();
        if (profile == null) {
            session.disconnect("Failed to get profile");
            return;
        }
        session.sendAsync(new ClientboundLoginFinishedPacket(profile));
    }
}
