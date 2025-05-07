package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.feature.player.World;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;

public class COBlockUpdateHandler implements PacketHandler<ClientboundBlockUpdatePacket, ServerSession> {
    @Override
    public ClientboundBlockUpdatePacket apply(final ClientboundBlockUpdatePacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            int minY = World.getCurrentDimension().minY();
            if (packet.getEntry().getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (World.getCurrentDimension().id() == DimensionRegistry.THE_NETHER.id()) {
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
