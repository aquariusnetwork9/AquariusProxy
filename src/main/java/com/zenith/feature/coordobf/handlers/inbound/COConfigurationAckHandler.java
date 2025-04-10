package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.zenith.Globals.MODULE;

public class COConfigurationAckHandler implements PacketHandler<ServerboundConfigurationAcknowledgedPacket, ServerSession> {
    @Override
    public ServerboundConfigurationAcknowledgedPacket apply(final ServerboundConfigurationAcknowledgedPacket packet, final ServerSession session) {
        session.getEventLoop().execute(() -> MODULE.get(CoordObfuscator.class).disconnect(session, "Server reconfiguring"));
        return packet;
    }
}
