package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.event.client.ClientConfigurationEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PostOutgoingPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;

import static com.aquarius.Globals.EVENT_BUS;

public class PostOutgoingFinishConfigurationHandler implements PostOutgoingPacketHandler<ServerboundFinishConfigurationPacket, ClientSession> {
    @Override
    public void accept(final ServerboundFinishConfigurationPacket packet, final ClientSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.GAME); // CONFIGURATION -> GAME
        EVENT_BUS.post(ClientConfigurationEvent.Exited.INSTANCE);
    }
}
