package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;

import static com.aquarius.Globals.CACHE;

public class PostOutgoingMoveVehicleHandler implements ClientEventLoopPacketHandler<ServerboundMoveVehiclePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundMoveVehiclePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setX(packet.getX());
        CACHE.getPlayerCache().setY(packet.getY());
        CACHE.getPlayerCache().setZ(packet.getZ());
        SpectatorSync.syncPlayerPositionWithSpectators();
        return true;
    }
}
