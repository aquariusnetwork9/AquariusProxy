package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.game.advancement.Advancement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateAdvancementsPacket;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;

import static com.zenith.Globals.CACHE;

public class UpdateAdvancementsHandler implements ClientEventLoopPacketHandler<ClientboundUpdateAdvancementsPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundUpdateAdvancementsPacket packet, @NonNull ClientSession session) {
        if (packet.isReset()) {
            CACHE.getStatsCache().getAdvancements().clear();
            CACHE.getStatsCache().getProgress().clear();
        }
        for (int i = 0; i < packet.getAdvancements().length; i++) {
            CACHE.getStatsCache().getAdvancements().add(packet.getAdvancements()[i]);
        }
        for (int i = 0; i < packet.getRemovedAdvancements().length; i++) {
            var advancement = packet.getRemovedAdvancements()[i];
            for (Advancement existing : CACHE.getStatsCache().getAdvancements()) {
                if (existing.getId().equals(advancement)) {
                    CACHE.getStatsCache().getAdvancements().remove(existing);
                    break;
                }
            }
        }
        packet.getProgress().forEach((id, criterions) -> CACHE.getStatsCache().getProgress().computeIfAbsent(id, s -> new HashMap<>()).putAll(criterions));
        return true;
    }
}
