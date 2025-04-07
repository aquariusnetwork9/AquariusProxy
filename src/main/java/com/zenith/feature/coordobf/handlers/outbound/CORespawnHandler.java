package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;

import static com.zenith.Globals.MODULE;

public class CORespawnHandler implements PacketHandler<ClientboundRespawnPacket, ServerSession> {
    @Override
    public ClientboundRespawnPacket apply(final ClientboundRespawnPacket packet, final ServerSession session) {
        MODULE.get(CoordObfuscator.class).onRespawn(session, packet.getCommonPlayerSpawnInfo().getDimension());
        return new ClientboundRespawnPacket(
            new PlayerSpawnInfo(
                packet.getCommonPlayerSpawnInfo().getDimension(),
                packet.getCommonPlayerSpawnInfo().getWorldName(),
                packet.getCommonPlayerSpawnInfo().getHashedSeed(),
                packet.getCommonPlayerSpawnInfo().getGameMode(),
                packet.getCommonPlayerSpawnInfo().getPreviousGamemode(),
                packet.getCommonPlayerSpawnInfo().isDebug(),
                packet.getCommonPlayerSpawnInfo().isFlat(),
                null, // strip pos
                packet.getCommonPlayerSpawnInfo().getPortalCooldown()
            ),
            packet.isKeepMetadata(),
            packet.isKeepAttributeModifiers()
        );
    }
}
