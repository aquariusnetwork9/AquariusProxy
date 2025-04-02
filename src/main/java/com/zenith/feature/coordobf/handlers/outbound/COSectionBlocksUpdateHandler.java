package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.zenith.Shared.*;

public class COSectionBlocksUpdateHandler implements PacketHandler<ClientboundSectionBlocksUpdatePacket, ServerSession> {
    @Override
    public ClientboundSectionBlocksUpdatePacket apply(final ClientboundSectionBlocksUpdatePacket packet, final ServerSession session) {
        List<BlockChangeEntry> entries = new ArrayList<>(Arrays.asList(packet.getEntries()));
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            DimensionData currentDimension = CACHE.getChunkCache().getCurrentDimension();
            if (currentDimension == null) return null;
            int minY = currentDimension.minY();
            entries.removeIf(entry -> entry.getY() <= minY + 5);
            if (currentDimension.id() == DimensionRegistry.THE_NETHER.id()) {
                entries.removeIf(entry -> entry.getY() >= 123);
            }
            if (entries.isEmpty()) {
                return null;
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundSectionBlocksUpdatePacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getChunkX()),
            packet.getChunkY(),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getChunkZ()),
            entries.stream().map(entry -> new BlockChangeEntry(
                coordObf.getCoordOffset(session).offsetX(entry.getX()),
                entry.getY(),
                coordObf.getCoordOffset(session).offsetZ(entry.getZ()),
                entry.getBlock()
            )).toArray(BlockChangeEntry[]::new)
        );
    }
}
