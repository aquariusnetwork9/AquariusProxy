package com.aquarius.network.server.handler.spectator.incoming;

import com.aquarius.Proxy;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

public class PlayerCommandSpectatorHandler implements PacketHandler<ServerboundPlayerCommandPacket, ServerSession> {

    @Override
    public ServerboundPlayerCommandPacket apply(ServerboundPlayerCommandPacket packet, ServerSession session) {
        // Sneak is no longer sent here as of 1.21.11 (see PlayerInputSpectatorHandler); this now only
        // ever sees START_SPRINTING/STOP_SPRINTING, kept for whatever real client still sends them.
        Entity cameraTarget = session.getCameraTarget();
        if (cameraTarget == null && packet.getState() == PlayerState.START_SPRINTING) {
            session.getSoundPacket().ifPresent(p -> {
                var connections = Proxy.getInstance().getActiveConnections().getArray();
                for (int i = 0; i < connections.length; i++) {
                    var connection = connections[i];
                    connection.send(p);
                }
            });
        }
        return null;
    }
}
