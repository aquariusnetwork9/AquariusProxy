package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;

import static com.aquarius.Globals.CACHE;

public class BlockChangedAckHandler implements ClientEventLoopPacketHandler<ClientboundBlockChangedAckPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundBlockChangedAckPacket packet, final ClientSession session) {
        CACHE.getChunkCache().getBlockStatePredictionHandler().endPredictionsUpTo(packet.getSequence());
        return true;
    }
}
