package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundAwardStatsPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class AwardStatsHandler implements ClientEventLoopPacketHandler<ClientboundAwardStatsPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundAwardStatsPacket packet, @NonNull ClientSession session) {
        CACHE.getStatsCache().getStatistics().putAll(packet.getStatistics());
        return true;
    }
}
