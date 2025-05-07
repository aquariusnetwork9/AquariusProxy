package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.feature.spectator.SpectatorSync;
import com.zenith.module.impl.AntiAFK;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerRotationPacket;

import static com.zenith.Globals.*;

public class PlayerRotationHandler implements ClientEventLoopPacketHandler<ClientboundPlayerRotationPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundPlayerRotationPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().setYaw(packet.getYaw());
        CACHE.getPlayerCache().setPitch(packet.getPitch());
        if (!Proxy.getInstance().hasActivePlayer()) {
            BOT.handlePlayerRotate();
        }
        SpectatorSync.syncPlayerPositionWithSpectators();
        MODULE.get(AntiAFK.class).handlePlayerPosRotate();
        return true;
    }
}
