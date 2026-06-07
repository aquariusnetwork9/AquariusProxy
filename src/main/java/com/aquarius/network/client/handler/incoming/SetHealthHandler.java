package com.aquarius.network.client.handler.incoming;

import com.aquarius.event.module.PlayerHealthChangedEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.*;

public class SetHealthHandler implements ClientEventLoopPacketHandler<ClientboundSetHealthPacket, ClientSession> {

    @Override
    public boolean applyAsync(@NonNull ClientboundSetHealthPacket packet, @NonNull ClientSession session) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (packet.getHealth() != player.getHealth()) {
            EVENT_BUS.postAsync(
                new PlayerHealthChangedEvent(packet.getHealth(), player.getHealth()));
        }

        if (packet.getFood() != player.getFood())
            CACHE_LOG.debug("Player food: {}", packet.getFood());
        if (packet.getSaturation() != player.getSaturation())
            CACHE_LOG.debug("Player saturation: {}", packet.getSaturation());
        if (packet.getHealth() != player.getHealth())
            CACHE_LOG.debug("Player health: {}", packet.getHealth());

        player
            .setFood(packet.getFood())
            .setSaturation(packet.getSaturation())
            .setHealth(packet.getHealth());
        return true;
    }
}
