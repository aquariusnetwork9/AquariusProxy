package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;

import static com.zenith.Shared.*;

public class COBlockDestructionHandler implements PacketHandler<ClientboundBlockDestructionPacket, ServerSession> {
    @Override
    public ClientboundBlockDestructionPacket apply(final ClientboundBlockDestructionPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            int minY = CACHE.getChunkCache().getCurrentDimension().minY();
            if (packet.getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (CACHE.getChunkCache().getCurrentDimension().name().contains("nether")) {
                if (packet.getY() >= 123) {
                    return null;
                }
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundBlockDestructionPacket(
            packet.getBreakerEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getStage()
        );
    }
}
