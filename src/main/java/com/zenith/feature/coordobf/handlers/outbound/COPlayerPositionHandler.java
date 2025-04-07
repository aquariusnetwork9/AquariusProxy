package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

import static com.zenith.Globals.MODULE;

public class COPlayerPositionHandler implements PacketHandler<ClientboundPlayerPositionPacket, ServerSession> {
    @Override
    public ClientboundPlayerPositionPacket apply(final ClientboundPlayerPositionPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        coordObf.onServerTeleport(session, packet.getX(), packet.getY(), packet.getZ(), packet.getTeleportId());
        return new ClientboundPlayerPositionPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch(),
            packet.getTeleportId(),
            packet.getRelative()
        );
    }
}
