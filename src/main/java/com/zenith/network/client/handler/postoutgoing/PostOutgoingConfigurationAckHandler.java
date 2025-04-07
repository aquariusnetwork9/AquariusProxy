package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.PostOutgoingPacketHandler;
import com.zenith.api.network.client.ClientSession;
import com.zenith.cache.CacheResetType;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.zenith.Globals.CACHE;

public class PostOutgoingConfigurationAckHandler implements PostOutgoingPacketHandler<ServerboundConfigurationAcknowledgedPacket, ClientSession> {
    @Override
    public void accept(final ServerboundConfigurationAcknowledgedPacket packet, final ClientSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.CONFIGURATION); // GAME -> CONFIGURATION
        CACHE.reset(CacheResetType.PROTOCOL_SWITCH);
    }
}
