package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.Proxy;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.util.Config.Client.Extra.CoordObfuscation.ObfuscationMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import static com.zenith.Shared.CONFIG;

public class COLoginHandler implements PacketHandler<ClientboundLoginPacket, ServerSession> {
    @Override
    public ClientboundLoginPacket apply(final ClientboundLoginPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.mode == ObfuscationMode.CONSTANT_OFFSET) {
            if (!Proxy.getInstance().getClient().isOnline()) {
                // prevent queue from leaking a constant offset
                session.disconnect("Queueing");
                return null;
            }
        }
        return new ClientboundLoginPacket(
            packet.getEntityId(),
            packet.isHardcore(),
            packet.getWorldNames(),
            packet.getMaxPlayers(),
            packet.getViewDistance(),
            packet.getSimulationDistance(),
            packet.isReducedDebugInfo(),
            packet.isEnableRespawnScreen(),
            packet.isDoLimitedCrafting(),
            new PlayerSpawnInfo(
                packet.getCommonPlayerSpawnInfo().getDimension(),
                packet.getCommonPlayerSpawnInfo().getWorldName(),
                packet.getCommonPlayerSpawnInfo().getHashedSeed(),
                packet.getCommonPlayerSpawnInfo().getGameMode(),
                packet.getCommonPlayerSpawnInfo().getPreviousGamemode(),
                packet.getCommonPlayerSpawnInfo().isDebug(),
                packet.getCommonPlayerSpawnInfo().isFlat(),
                null,
                packet.getCommonPlayerSpawnInfo().getPortalCooldown()
            ),
            false
        );
    }
}
