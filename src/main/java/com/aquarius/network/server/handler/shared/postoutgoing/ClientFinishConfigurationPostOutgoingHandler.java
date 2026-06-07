package com.aquarius.network.server.handler.shared.postoutgoing;

import com.aquarius.network.codec.PostOutgoingPacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;

public class ClientFinishConfigurationPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundFinishConfigurationPacket, ServerSession> {
    @Override
    public void accept(final ClientboundFinishConfigurationPacket packet, final ServerSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.GAME);
    }
}
