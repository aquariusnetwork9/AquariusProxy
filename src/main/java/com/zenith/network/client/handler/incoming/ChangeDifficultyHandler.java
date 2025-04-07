package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;

import static com.zenith.Globals.CACHE;

public class ChangeDifficultyHandler implements ClientEventLoopPacketHandler<ClientboundChangeDifficultyPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundChangeDifficultyPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setDifficulty(packet.getDifficulty());
        CACHE.getPlayerCache().setDifficultyLocked(packet.isDifficultyLocked());
        return true;
    }
}
