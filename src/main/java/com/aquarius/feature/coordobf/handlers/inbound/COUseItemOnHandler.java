package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.feature.player.World;
import com.aquarius.mc.dimension.DimensionRegistry;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class COUseItemOnHandler implements PacketHandler<ServerboundUseItemOnPacket, ServerSession> {
    @Override
    public ServerboundUseItemOnPacket apply(final ServerboundUseItemOnPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            var dim = World.getCurrentDimension();
            int minY = dim.minY();
            if (packet.getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (dim == DimensionRegistry.THE_NETHER.get()) {
                if (packet.getY() >= 123) {
                    return null;
                }
            }
        }
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ServerboundUseItemOnPacket(
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()),
            packet.getFace(),
            packet.getHand(),
            packet.getCursorX(),
            packet.getCursorY(),
            packet.getCursorZ(),
            packet.isInsideBlock(),
            packet.isHitWorldBorder(),
            packet.getSequence());
    }
}
