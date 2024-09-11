package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;

import static com.zenith.Shared.MODULE;

public class CORespawnHandler implements PacketHandler<ClientboundRespawnPacket, ServerSession> {
    @Override
    public ClientboundRespawnPacket apply(final ClientboundRespawnPacket packet, final ServerSession session) {
        // todo: we need better handling of respawns that cross dimensions
        //  if a player is being respawned to the nether we need to shift their offset with the same coordinate scale
        //  forget chunk packets at overworld coordinates are still sent after this respawn packet
        //  we should only start shifting the scale when the next player position packet is received
        //  also need to ensure this expected packet order from the server is what we actually see in all cases
        //  including queue to in-game server switch

        // forced transfer is a hacky solution just so we can force a clean client state and regenerate offset safely
//        session.transfer(CONFIG.server.getProxyAddressForTransfer(), CONFIG.server.getProxyPortForTransfer());
//        return null;

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
