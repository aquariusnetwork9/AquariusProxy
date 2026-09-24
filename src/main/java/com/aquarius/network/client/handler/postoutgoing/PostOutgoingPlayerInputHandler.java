package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

import static com.aquarius.Globals.CACHE;

/**
 * Sneak is no longer a discrete PlayerState command as of 1.21.11 — it's the `shift` flag here,
 * sent alongside the rest of the bot's movement input. Mirrors the old START_SNEAKING/STOP_SNEAKING
 * PostOutgoingPlayerCommandHandler cases: update PlayerCache and re-sync pose only on an actual flip.
 */
public class PostOutgoingPlayerInputHandler implements ClientEventLoopPacketHandler<ServerboundPlayerInputPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundPlayerInputPacket packet, final ClientSession session) {
        boolean wasSneaking = CACHE.getPlayerCache().isSneaking();
        if (packet.isShift() != wasSneaking) {
            CACHE.getPlayerCache().setSneaking(packet.isShift());
            SpectatorSync.sendPlayerPose();
        }
        return true;
    }
}
