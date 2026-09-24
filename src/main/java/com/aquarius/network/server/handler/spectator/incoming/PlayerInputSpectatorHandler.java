package com.aquarius.network.server.handler.spectator.incoming;

import com.aquarius.Proxy;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

/**
 * As of 1.21.11, a real spectator client sends sneak/sprint as continuous flags here instead of
 * discrete PlayerState commands (see the now-trimmed PlayerCommandSpectatorHandler). Edge-detects
 * the start of each against ServerSession's own spectatorWasSneaking/spectatorWasSprinting to
 * preserve the original start-only trigger semantics.
 */
public class PlayerInputSpectatorHandler implements PacketHandler<ServerboundPlayerInputPacket, ServerSession> {

    @Override
    public ServerboundPlayerInputPacket apply(ServerboundPlayerInputPacket packet, ServerSession session) {
        boolean startedSneaking = packet.isShift() && !session.isSpectatorWasSneaking();
        boolean startedSprinting = packet.isSprint() && !session.isSpectatorWasSprinting();
        session.setSpectatorWasSneaking(packet.isShift());
        session.setSpectatorWasSprinting(packet.isSprint());

        Entity cameraTarget = session.getCameraTarget();
        if (cameraTarget != null) {
            if (startedSneaking) {
                session.setCameraTarget(null);
                session.send(new ClientboundSetCameraPacket(session.getSpectatorSelfEntityId()));
                SpectatorSync.syncSpectatorPositionToEntity(session, cameraTarget);
            }
        } else if (startedSneaking || startedSprinting) {
            session.getSoundPacket().ifPresent(p -> {
                var connections = Proxy.getInstance().getActiveConnections().getArray();
                for (int i = 0; i < connections.length; i++) {
                    var connection = connections[i];
                    connection.send(p);
                }
            });
        }
        return packet;
    }
}
