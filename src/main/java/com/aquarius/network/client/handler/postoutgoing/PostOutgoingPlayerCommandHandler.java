package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import static com.aquarius.Globals.CACHE;

public class PostOutgoingPlayerCommandHandler implements ClientEventLoopPacketHandler<ServerboundPlayerCommandPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundPlayerCommandPacket packet, final ClientSession session) {
        if (packet.getEntityId() != CACHE.getPlayerCache().getEntityId()) return true;
        switch (packet.getState()) {
            case START_SNEAKING -> {
                CACHE.getPlayerCache().setSneaking(true);
                SpectatorSync.sendPlayerPose();
            }
            case STOP_SNEAKING -> {
                CACHE.getPlayerCache().setSneaking(false);
                SpectatorSync.sendPlayerPose();
            }
            case START_SPRINTING -> CACHE.getPlayerCache().setSprinting(true);
            case STOP_SPRINTING -> CACHE.getPlayerCache().setSprinting(false);
        }
        return true;
    }
}
