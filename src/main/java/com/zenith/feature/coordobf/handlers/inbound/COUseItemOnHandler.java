package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.feature.player.World;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;

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
            if (dim.id() == DimensionRegistry.THE_NETHER.id()) {
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
            packet.getSequence());
    }
}
