package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerLookAtPacket;

import static com.aquarius.Globals.MODULE;

public class COPlayerLookAtHandler implements PacketHandler<ClientboundPlayerLookAtPacket, ServerSession> {
    @Override
    public ClientboundPlayerLookAtPacket apply(final ClientboundPlayerLookAtPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundPlayerLookAtPacket(
            packet.getOrigin(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ())
        );
    }
}
