package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.feature.player.World;
import com.aquarius.mc.dimension.DimensionRegistry;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class COBlockDestructionHandler implements PacketHandler<ClientboundBlockDestructionPacket, ServerSession> {
    @Override
    public ClientboundBlockDestructionPacket apply(final ClientboundBlockDestructionPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            int minY = World.getCurrentDimension().minY();
            if (packet.getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (World.getCurrentDimension() == DimensionRegistry.THE_NETHER.get()) {
                if (packet.getY() >= 123) {
                    return null;
                }
            }
        }
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundBlockDestructionPacket(
            packet.getBreakerEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getStage()
        );
    }
}
