package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import static com.zenith.Globals.MODULE;

public class COSoundHandler implements PacketHandler<ClientboundSoundPacket, ServerSession> {
    @Override
    public ClientboundSoundPacket apply(final ClientboundSoundPacket packet, final ServerSession session) {
        if (packet.getSound().getName().toLowerCase().contains("ender_eye")) return null;
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundSoundPacket(
            packet.getSound(),
            packet.getCategory(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getVolume(),
            packet.getPitch(),
            packet.getSeed()
        );
    }
}
