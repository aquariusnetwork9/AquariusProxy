package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.mc.dimension.DimensionData;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.CONFIG;

public class COSectionBlocksUpdateHandler implements PacketHandler<ClientboundSectionBlocksUpdatePacket, ServerSession> {
    @Override
    public ClientboundSectionBlocksUpdatePacket apply(final ClientboundSectionBlocksUpdatePacket packet, final ServerSession session) {
        List<BlockChangeEntry> entries = new ArrayList<>(Arrays.asList(packet.getEntries()));
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            DimensionData currentDimension = CACHE.getChunkCache().getCurrentDimension();
            if (currentDimension == null) return null;
            int minY = currentDimension.minY();
            entries.removeIf(entry -> entry.getY() <= minY + 5);
            if (currentDimension.name().contains("nether")) {
                entries.removeIf(entry -> entry.getY() >= 123);
            }
            if (entries.isEmpty()) {
                return null;
            }
        }
        return new ClientboundSectionBlocksUpdatePacket(
            session.getCoordOffset().offsetChunkX(packet.getChunkX()),
            packet.getChunkY(),
            session.getCoordOffset().offsetChunkZ(packet.getChunkZ()),
            entries.stream().map(entry -> new BlockChangeEntry(
                session.getCoordOffset().offsetX(entry.getX()),
                entry.getY(),
                session.getCoordOffset().offsetZ(entry.getZ()),
                entry.getBlock()
            )).toArray(BlockChangeEntry[]::new)
        );
    }
}
