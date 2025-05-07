package com.zenith.network.client.handler.incoming;

import com.zenith.event.module.ServerPlayerLogoutInVisualRangeEvent;
import com.zenith.event.server.ServerPlayerDisconnectedEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EVENT_BUS;

public class PlayerInfoRemoveHandler implements ClientEventLoopPacketHandler<ClientboundPlayerInfoRemovePacket, ClientSession> {
    @Override
    public boolean applyAsync(ClientboundPlayerInfoRemovePacket packet, ClientSession session) {
        List<UUID> profileIds = packet.getProfileIds();
        for (int i = 0; i < profileIds.size(); i++) {
            final UUID profileId = profileIds.get(i);
            Optional<PlayerListEntry> playerEntry = CACHE.getTabListCache().remove(profileId);
            playerEntry.ifPresent(e -> {
                EVENT_BUS.postAsync(new ServerPlayerDisconnectedEvent(e));
                CACHE.getEntityCache().getRecentlyRemovedPlayer(e.getProfileId())
                    .filter(entityPlayer -> !entityPlayer.isSelfPlayer())
                    .ifPresent(entityPlayer -> EVENT_BUS.postAsync(new ServerPlayerLogoutInVisualRangeEvent(e, entityPlayer)));
            });
        }
        return true;
    }
}
