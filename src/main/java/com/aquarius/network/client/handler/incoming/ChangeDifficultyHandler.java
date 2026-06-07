package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;

import static com.aquarius.Globals.CACHE;

public class ChangeDifficultyHandler implements ClientEventLoopPacketHandler<ClientboundChangeDifficultyPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundChangeDifficultyPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setDifficulty(packet.getDifficulty());
        CACHE.getPlayerCache().setDifficultyLocked(packet.isDifficultyLocked());
        return true;
    }
}
