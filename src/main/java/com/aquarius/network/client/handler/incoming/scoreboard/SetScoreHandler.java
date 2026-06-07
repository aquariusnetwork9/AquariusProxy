package com.aquarius.network.client.handler.incoming.scoreboard;

import com.aquarius.cache.data.scoreboard.Score;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class SetScoreHandler implements ClientEventLoopPacketHandler<ClientboundSetScorePacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundSetScorePacket packet, @NonNull ClientSession session) {
        var objective = CACHE.getScoreboardCache().get(packet.getObjective());
        if (objective != null) {
            objective.getScores().put(packet.getOwner(), new Score(packet));
        }
        return true;
    }
}
