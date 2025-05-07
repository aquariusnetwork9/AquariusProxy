package com.zenith.network.client.handler.incoming.level;

import com.zenith.feature.spectator.SpectatorSync;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class ForgetLevelChunkHandler implements ClientEventLoopPacketHandler<ClientboundForgetLevelChunkPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundForgetLevelChunkPacket packet, @NonNull ClientSession session) {
        CACHE.getChunkCache().remove(packet.getX(), packet.getZ());
        SpectatorSync.checkSpectatorPositionOutOfRender(packet.getX(), packet.getZ());
        return true;
    }
}
