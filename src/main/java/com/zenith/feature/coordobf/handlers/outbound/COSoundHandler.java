package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

public class COSoundHandler implements PacketHandler<ClientboundSoundPacket, ServerSession> {
    @Override
    public ClientboundSoundPacket apply(final ClientboundSoundPacket packet, final ServerSession session) {
        if (packet.getSound().getName().toLowerCase().contains("ender_eye")) return null;
        return new ClientboundSoundPacket(
            packet.getSound(),
            packet.getCategory(),
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getVolume(),
            packet.getPitch(),
            packet.getSeed()
        );
    }
}
