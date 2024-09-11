package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;

public class COLevelEventHandler implements PacketHandler<ClientboundLevelEventPacket, ServerSession> {
    @Override
    public ClientboundLevelEventPacket apply(final ClientboundLevelEventPacket packet, final ServerSession session) {
        if (packet.getEvent() == LevelEventType.PARTICLES_EYE_OF_ENDER_DEATH) return null;
        if (packet.getEvent() == LevelEventType.ANIMATION_END_GATEWAY_SPAWN) return null;
        if (packet.getEvent() == LevelEventType.END_PORTAL_FRAME_FILL) return null;
        if (packet.getEvent() == LevelEventType.SOUND_DRAGON_DEATH) return null;
        return new ClientboundLevelEventPacket(
            packet.getEvent(),
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getData(),
            packet.isBroadcast()
        );
    }
}
