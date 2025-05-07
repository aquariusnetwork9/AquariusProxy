package com.zenith.network.client.handler.incoming.entity;

import com.zenith.feature.spectator.SpectatorSync;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveVehiclePacket;

import static com.zenith.Globals.CACHE;

public class MoveVehicleHandler implements ClientEventLoopPacketHandler<ClientboundMoveVehiclePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundMoveVehiclePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setX(packet.getX());
        CACHE.getPlayerCache().setY(packet.getY());
        CACHE.getPlayerCache().setZ(packet.getZ());
        SpectatorSync.syncPlayerPositionWithSpectators();
        return true;
    }
}
