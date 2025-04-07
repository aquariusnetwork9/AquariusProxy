package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;

import static com.zenith.Globals.CACHE;

public class PostOutgoingPlayerRotationHandler implements ClientEventLoopPacketHandler<ServerboundMovePlayerRotPacket, ClientSession> {
    @Override
    public boolean applyAsync(ServerboundMovePlayerRotPacket packet, ClientSession session) {
        CACHE.getPlayerCache()
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch());
        SpectatorSync.syncPlayerPositionWithSpectators();
        return true;
    }
}
