package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;

public class COSetDefaultSpawnPositionHandler implements PacketHandler<ClientboundSetDefaultSpawnPositionPacket, ServerSession> {
    @Override
    public ClientboundSetDefaultSpawnPositionPacket apply(final ClientboundSetDefaultSpawnPositionPacket packet, final ServerSession session) {
        // no need for clients to know this
        // and could reveal offset under certain conditions
        return new ClientboundSetDefaultSpawnPositionPacket(
            new GlobalPos(packet.getGlobalPos().getDimension(), 0, 0, 0),
            packet.getYaw(),
            packet.getPitch());
    }
}
