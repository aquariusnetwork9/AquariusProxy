package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

import static com.zenith.Globals.MODULE;

public class COAcceptTeleportationHandler implements PacketHandler<ServerboundAcceptTeleportationPacket, ServerSession> {
    @Override
    public ServerboundAcceptTeleportationPacket apply(final ServerboundAcceptTeleportationPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        var playerState = coordObf.getPlayerState(session);
        if (!playerState.isInGame() || session.isSpectator()) return packet;
        var serverTp = playerState.getServerTeleports().peek();
        if (serverTp == null || serverTp.id() != packet.getId()) {
            coordObf.info("Reconnecting {} because they are trying to accept an unexpected teleport id: {}", session.getName(), packet.getId());
            coordObf.reconnect(session);
            return null;
        }
        return packet;
    }
}
