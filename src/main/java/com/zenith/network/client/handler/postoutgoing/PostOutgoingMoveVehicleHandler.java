package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;

import static com.zenith.Globals.CACHE;

public class PostOutgoingMoveVehicleHandler implements ClientEventLoopPacketHandler<ServerboundMoveVehiclePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundMoveVehiclePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setX(packet.getX());
        CACHE.getPlayerCache().setY(packet.getY());
        CACHE.getPlayerCache().setZ(packet.getZ());
        return true;
    }
}
