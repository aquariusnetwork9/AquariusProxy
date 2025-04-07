package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundUpdateEnabledFeaturesPacket;

import static com.zenith.Globals.CACHE;

public class UpdateEnabledFeaturesHandler implements ClientEventLoopPacketHandler<ClientboundUpdateEnabledFeaturesPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundUpdateEnabledFeaturesPacket packet, final ClientSession session) {
        CACHE.getConfigurationCache().setEnabledFeatures(packet.getFeatures());
        return true;
    }
}
