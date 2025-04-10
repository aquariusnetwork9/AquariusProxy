package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.coordobf.ObfPlayerState;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

import static com.zenith.Globals.MODULE;

public class COAcceptTeleportationHandler implements PacketHandler<ServerboundAcceptTeleportationPacket, ServerSession> {
    @Override
    public ServerboundAcceptTeleportationPacket apply(final ServerboundAcceptTeleportationPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscator.class);
        ObfPlayerState playerState = coordObf.getPlayerState(session);
        if (!playerState.isInGame() || session.isSpectator()) return packet;
        ObfPlayerState.ServerTeleport serverTp = playerState.getServerTeleport();
        if (serverTp == null || serverTp.id() != packet.getId()) {
            coordObf.info("Reconnecting {} because they are trying to accept an unexpected teleport id: {}", session.getName(), packet.getId());
            coordObf.reconnect(session);
            return null;
        }
        return packet;
    }
}
