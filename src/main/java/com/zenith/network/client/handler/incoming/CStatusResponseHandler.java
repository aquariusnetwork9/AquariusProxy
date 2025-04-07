package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.status.clientbound.ClientboundStatusResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.status.serverbound.ServerboundPingRequestPacket;

public class CStatusResponseHandler implements PacketHandler<ClientboundStatusResponsePacket, ClientSession> {
    @Override
    public ClientboundStatusResponsePacket apply(final ClientboundStatusResponsePacket packet, final ClientSession session) {
        session.send(new ServerboundPingRequestPacket(System.currentTimeMillis()));
        return null;
    }
}
