package com.aquarius.network.server.handler.spectator.outgoing;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import static com.aquarius.Globals.SERVER_LOG;

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
