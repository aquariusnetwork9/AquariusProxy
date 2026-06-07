package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

import static com.aquarius.Globals.MODULE;

public class COPlayerPositionHandler implements PacketHandler<ClientboundPlayerPositionPacket, ServerSession> {
    @Override
    public ClientboundPlayerPositionPacket apply(final ClientboundPlayerPositionPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        coordObf.onServerTeleport(session, packet.getX(), packet.getY(), packet.getZ(), packet.getId(), packet.getRelatives());
        return new ClientboundPlayerPositionPacket(
            packet.getId(),
            packet.getRelatives().contains(PositionElement.X)
                ? packet.getX()
                : coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            packet.getRelatives().contains(PositionElement.Z)
                ? packet.getZ()
                : coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ(),
            packet.getYaw(),
            packet.getPitch(),
            packet.getRelatives()
        );
    }
}
