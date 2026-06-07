package com.aquarius.network.client.handler.incoming;

import com.aquarius.Proxy;
import com.aquarius.event.client.ClientConfigurationEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.aquarius.Globals.EVENT_BUS;

public class CStartConfigurationHandler implements PacketHandler<ClientboundStartConfigurationPacket, ClientSession> {
    @Override
    public ClientboundStartConfigurationPacket apply(final ClientboundStartConfigurationPacket packet, final ClientSession session) {
        EVENT_BUS.post(ClientConfigurationEvent.Entering.INSTANCE);
        session.setOnline(false);
        session.switchInboundState(ProtocolState.CONFIGURATION);
        if (!Proxy.getInstance().hasActivePlayer()) {
            session.send(new ServerboundConfigurationAcknowledgedPacket());
            return null;
        }
        return packet;
    }
}
