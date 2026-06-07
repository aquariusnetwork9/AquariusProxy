package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.Proxy;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.aquarius.Globals.MODULE;

public class COCStartConfigurationHandler implements PacketHandler<ClientboundStartConfigurationPacket, ServerSession> {
    @Override
    public ClientboundStartConfigurationPacket apply(final ClientboundStartConfigurationPacket packet, final ServerSession session) {
        if (Proxy.getInstance().getActivePlayer() == session) {
            // ensure we don't get stuck in a state where the bot is unaware it needs to enter configuration
            Proxy.getInstance().getClient().sendAsync(new ServerboundConfigurationAcknowledgedPacket());
        }
        MODULE.get(CoordObfuscation.class).disconnect(session, "Server reconfiguring", "Server re-entering us to configuration state, probable world switch");
        return null;
    }
}

