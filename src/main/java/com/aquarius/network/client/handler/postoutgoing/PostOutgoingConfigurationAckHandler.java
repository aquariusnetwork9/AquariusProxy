package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.cache.CacheResetType;
import com.aquarius.event.client.ClientConfigurationEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PostOutgoingPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.EVENT_BUS;

public class PostOutgoingConfigurationAckHandler implements PostOutgoingPacketHandler<ServerboundConfigurationAcknowledgedPacket, ClientSession> {
    @Override
    public void accept(final ServerboundConfigurationAcknowledgedPacket packet, final ClientSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.CONFIGURATION); // GAME -> CONFIGURATION
        CACHE.reset(CacheResetType.PROTOCOL_SWITCH);
        EVENT_BUS.post(ClientConfigurationEvent.Entered.INSTANCE);
    }
}
