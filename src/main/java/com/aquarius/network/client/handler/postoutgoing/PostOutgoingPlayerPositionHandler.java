package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.aquarius.Globals.CACHE;

public class PostOutgoingPlayerPositionHandler implements ClientEventLoopPacketHandler<ServerboundMovePlayerPosPacket, ClientSession> {

    @Override
    public boolean applyAsync(ServerboundMovePlayerPosPacket packet, ClientSession session) {
        CACHE.getPlayerCache()
                .setX(packet.getX())
                .setY(packet.getY())
                .setZ(packet.getZ());
        SpectatorSync.syncPlayerPositionWithSpectators();
        return true;
    }
}
