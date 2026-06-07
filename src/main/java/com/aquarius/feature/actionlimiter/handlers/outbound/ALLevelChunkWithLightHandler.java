package com.aquarius.feature.actionlimiter.handlers.outbound;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import com.aquarius.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

public class ALLevelChunkWithLightHandler implements PacketHandler<ClientboundLevelChunkWithLightPacket, ServerSession> {
    @Override
    public ClientboundLevelChunkWithLightPacket apply(final ClientboundLevelChunkWithLightPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowMovement) return packet;
        var blockX = packet.getX() << 4;
        var blockZ = packet.getZ() << 4;

        if (MathHelper.distance2d(
            blockX,
            blockZ,
            CONFIG.client.extra.actionLimiter.movementHomeX,
            CONFIG.client.extra.actionLimiter.movementHomeZ
        ) > CONFIG.client.extra.actionLimiter.movementDistance + 64 + (CACHE.getChunkCache().getServerViewDistance() * 16)) {
            session.disconnect("ActionLimiter: Movement not allowed");
            return null;
        }
        return packet;
    }
}
