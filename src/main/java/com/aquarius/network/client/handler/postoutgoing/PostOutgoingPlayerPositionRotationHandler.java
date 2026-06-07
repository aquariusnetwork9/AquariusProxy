package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import static com.aquarius.Globals.CACHE;

public class PostOutgoingPlayerPositionRotationHandler implements ClientEventLoopPacketHandler<ServerboundMovePlayerPosRotPacket, ClientSession> {
    @Override
    public boolean applyAsync(ServerboundMovePlayerPosRotPacket packet, ClientSession session) {
        CACHE.getPlayerCache()
                .setX(packet.getX())
                .setY(packet.getY())
                .setZ(packet.getZ())
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch());
        SpectatorSync.syncPlayerPositionWithSpectators();
        if (!CACHE.getPlayerCache().isClientLoaded()) {
            session.send(ServerboundPlayerLoadedPacket.INSTANCE);
        }
        return true;
    }
}
