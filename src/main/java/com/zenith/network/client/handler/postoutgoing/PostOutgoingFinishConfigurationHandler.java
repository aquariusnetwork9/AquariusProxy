package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.PostOutgoingPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;

public class PostOutgoingFinishConfigurationHandler implements PostOutgoingPacketHandler<ServerboundFinishConfigurationPacket, ClientSession> {
    @Override
    public void accept(final ServerboundFinishConfigurationPacket packet, final ClientSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.GAME); // CONFIGURATION -> GAME
    }
}
