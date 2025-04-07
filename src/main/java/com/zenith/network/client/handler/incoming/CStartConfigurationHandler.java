package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

public class CStartConfigurationHandler implements PacketHandler<ClientboundStartConfigurationPacket, ClientSession> {
    @Override
    public ClientboundStartConfigurationPacket apply(final ClientboundStartConfigurationPacket packet, final ClientSession session) {
        session.switchInboundState(ProtocolState.CONFIGURATION);
        if (!Proxy.getInstance().hasActivePlayer()) {
            session.send(new ServerboundConfigurationAcknowledgedPacket());
            return null;
        }
        return packet;
    }
}
