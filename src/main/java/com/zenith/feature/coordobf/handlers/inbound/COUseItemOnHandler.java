package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.CONFIG;

public class COUseItemOnHandler implements PacketHandler<ServerboundUseItemOnPacket, ServerSession> {
    @Override
    public ServerboundUseItemOnPacket apply(final ServerboundUseItemOnPacket packet, final ServerSession session) {
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
        return new ServerboundUseItemOnPacket(
            session.getCoordOffset().reverseOffsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().reverseOffsetZ(packet.getZ()),
            packet.getFace(),
            packet.getHand(),
            packet.getCursorX(),
            packet.getCursorY(),
            packet.getCursorZ(),
            packet.isInsideBlock(),
            packet.getSequence());
    }
}
