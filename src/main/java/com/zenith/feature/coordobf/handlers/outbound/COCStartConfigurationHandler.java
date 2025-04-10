package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.Proxy;
import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.zenith.Globals.MODULE;

public class COCStartConfigurationHandler implements PacketHandler<ClientboundStartConfigurationPacket, ServerSession> {
    @Override
    public ClientboundStartConfigurationPacket apply(final ClientboundStartConfigurationPacket packet, final ServerSession session) {
        if (Proxy.getInstance().getActivePlayer() == session) {
            // ensure we don't get stuck in a state where the bot is unaware it needs to enter configuration
            Proxy.getInstance().getClient().sendAsync(new ServerboundConfigurationAcknowledgedPacket());
        }
        MODULE.get(CoordObfuscator.class).disconnect(session, "Server reconfiguring");
        return null;
    }
}

