package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class ForgetLevelChunkHandler implements ClientEventLoopPacketHandler<ClientboundForgetLevelChunkPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundForgetLevelChunkPacket packet, @NonNull ClientSession session) {
        CACHE.getChunkCache().remove(packet.getX(), packet.getZ());
        SpectatorSync.checkSpectatorPositionOutOfRender(packet.getX(), packet.getZ());
        return true;
    }
}
