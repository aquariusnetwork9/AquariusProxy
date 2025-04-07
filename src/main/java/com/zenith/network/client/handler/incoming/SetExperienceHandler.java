package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;

import static com.zenith.Globals.CACHE;

public class SetExperienceHandler implements ClientEventLoopPacketHandler<ClientboundSetExperiencePacket, ClientSession> {
    @Override
    public boolean applyAsync(ClientboundSetExperiencePacket packet, ClientSession session) {
        CACHE.getPlayerCache().getThePlayer()
                .setTotalExperience(packet.getTotalExperience())
                .setLevel(packet.getLevel())
                .setExperience(packet.getExperience());
        return true;
    }
}
