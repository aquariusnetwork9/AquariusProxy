package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

import static com.zenith.Shared.MODULE;

public class COPlayerPositionHandler implements PacketHandler<ClientboundPlayerPositionPacket, ServerSession> {
    @Override
    public ClientboundPlayerPositionPacket apply(final ClientboundPlayerPositionPacket packet, final ServerSession session) {
        MODULE.get(CoordObfuscator.class).onServerTeleport(session, packet.getX(), packet.getY(), packet.getZ(), packet.getTeleportId());
        return new ClientboundPlayerPositionPacket(
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch(),
            packet.getTeleportId(),
            packet.getRelative()
        );
    }
}
