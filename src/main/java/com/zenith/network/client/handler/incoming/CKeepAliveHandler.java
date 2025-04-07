package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;

import static com.zenith.Globals.CONFIG;

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
