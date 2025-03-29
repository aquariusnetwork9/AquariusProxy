package com.zenith.network.client.handler.incoming;

import com.zenith.network.client.ClientSession;
import com.zenith.network.registry.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;

import static com.zenith.Shared.CONFIG;

public class CKeepAliveHandler implements PacketHandler<ClientboundKeepAlivePacket, ClientSession> {
    public static final CKeepAliveHandler INSTANCE = new CKeepAliveHandler();
    @Override
    public ClientboundKeepAlivePacket apply(final ClientboundKeepAlivePacket packet, final ClientSession session) {
        if (CONFIG.client.automaticKeepAliveManagement) {
            session.send(new ServerboundKeepAlivePacket(packet.getPingId()));
            return null;
        }
        return packet;
    }
}
