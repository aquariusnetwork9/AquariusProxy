package com.aquarius.network.client.handler.incoming;

import com.aquarius.Proxy;
import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.module.impl.AntiAFK;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerRotationPacket;

import static com.aquarius.Globals.*;

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
