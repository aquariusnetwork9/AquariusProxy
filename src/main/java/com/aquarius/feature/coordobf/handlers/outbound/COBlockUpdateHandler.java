package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.feature.player.World;
import com.aquarius.mc.dimension.DimensionRegistry;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class COBlockUpdateHandler implements PacketHandler<ClientboundBlockUpdatePacket, ServerSession> {
    @Override
    public ClientboundBlockUpdatePacket apply(final ClientboundBlockUpdatePacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            int minY = World.getCurrentDimension().minY();
            if (packet.getEntry().getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (World.getCurrentDimension() == DimensionRegistry.THE_NETHER.get()) {
                if (packet.getEntry().getY() >= 123) {
                    return null;
                }
            }
        }
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundBlockUpdatePacket(
            new BlockChangeEntry(
                coordObf.getCoordOffset(session).offsetX(packet.getEntry().getX()),
                packet.getEntry().getY(),
                coordObf.getCoordOffset(session).offsetZ(packet.getEntry().getZ()),
                packet.getEntry().getBlock())
        );
    }
}
