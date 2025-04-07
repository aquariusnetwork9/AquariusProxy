package com.zenith.network.client.handler.incoming.scoreboard;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class SetDisplayObjectiveHandler implements ClientEventLoopPacketHandler<ClientboundSetDisplayObjectivePacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundSetDisplayObjectivePacket packet, @NonNull ClientSession session) {
        CACHE.getScoreboardCache().setPositionObjective(packet.getPosition(), packet.getName());
        return true;
    }
}
