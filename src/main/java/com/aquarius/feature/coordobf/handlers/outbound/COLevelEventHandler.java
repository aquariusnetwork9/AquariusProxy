package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;

import static com.aquarius.Globals.MODULE;

public class COLevelEventHandler implements PacketHandler<ClientboundLevelEventPacket, ServerSession> {
    @Override
    public ClientboundLevelEventPacket apply(final ClientboundLevelEventPacket packet, final ServerSession session) {
        if (packet.getEvent() == LevelEventType.PARTICLES_EYE_OF_ENDER_DEATH) return null;
        if (packet.getEvent() == LevelEventType.ANIMATION_END_GATEWAY_SPAWN) return null;
        if (packet.getEvent() == LevelEventType.END_PORTAL_FRAME_FILL) return null;
        if (packet.getEvent() == LevelEventType.SOUND_DRAGON_DEATH) return null;
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundLevelEventPacket(
            packet.getEvent(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getData(),
            packet.isBroadcast()
        );
    }
}
