package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import static com.zenith.Globals.MODULE;

public class COSoundHandler implements PacketHandler<ClientboundSoundPacket, ServerSession> {
    @Override
    public ClientboundSoundPacket apply(final ClientboundSoundPacket packet, final ServerSession session) {
        if (packet.getSound().getName().toLowerCase().contains("ender_eye")) return null;
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
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
