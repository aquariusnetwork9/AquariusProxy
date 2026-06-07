package com.aquarius.network.server.handler.shared.postoutgoing;

import com.aquarius.network.codec.PostOutgoingPacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;

public class ClientStartConfigurationPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundStartConfigurationPacket, ServerSession> {
    @Override
    public void accept(final ClientboundStartConfigurationPacket packet, final ServerSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.CONFIGURATION);
    }
}
