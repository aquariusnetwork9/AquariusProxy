package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import static com.zenith.Globals.SERVER_LOG;

public class LoginSpectatorOutgoingHandler implements PacketHandler<ClientboundLoginPacket, ServerSession> {
    @Override
    public ClientboundLoginPacket apply(final ClientboundLoginPacket packet, final ServerSession session) {
        if (session.isLoggedIn()) {
            if (session.canTransfer()) {
                SERVER_LOG.info("Reconnecting spectator: {} because client is switching servers", session.getName());
                session.transferToSpectator();
            } else {
                session.disconnect("Client is switching servers");
            }
            return null;
        }
        return packet;
    }
}
