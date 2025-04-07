package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundAwardStatsPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class AwardStatsHandler implements ClientEventLoopPacketHandler<ClientboundAwardStatsPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundAwardStatsPacket packet, @NonNull ClientSession session) {
        CACHE.getStatsCache().getStatistics().putAll(packet.getStatistics());
        return true;
    }
}
