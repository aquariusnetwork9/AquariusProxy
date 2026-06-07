package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

public class SPlayerLoadedHandler implements PacketHandler<ServerboundPlayerLoadedPacket, ServerSession> {
    @Override
    public ServerboundPlayerLoadedPacket apply(final ServerboundPlayerLoadedPacket packet, final ServerSession session) {
        session.setClientLoaded(true);
        return packet;
    }
}
