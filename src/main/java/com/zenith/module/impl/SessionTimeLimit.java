package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.feature.api.vcapi.VcApi;
import com.zenith.module.api.Module;
import com.zenith.network.server.ServerSession;
import com.zenith.util.ComponentSerializer;
import lombok.Getter;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * Displays an in-game warning when the 2b2t non-prio session time limit is about to be reached
 */
public class SessionTimeLimit extends Module {
    @Getter private Duration sessionTimeLimit = Duration.ofHours(8);
    private Instant lastUpdatedSessionTimeLimit = Instant.EPOCH;
    private Instant lastWarningSent = Instant.EPOCH;

    public SessionTimeLimit() {
        EXECUTOR.schedule(() -> {
            EXECUTOR.scheduleAtFixedRate(this::updateSessionTimeLimit, 0, 6, TimeUnit.HOURS);
        }, ThreadLocalRandom.current().nextInt(1, 60), TimeUnit.MINUTES);
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientTickEvent.class, this::handleClientTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.sessionTimeLimit.enabled;
    }

    private void handleClientTick(ClientTickEvent event) {
        if (Proxy.getInstance().isPrio()) return;
        if (!Proxy.getInstance().hasActivePlayer()) return;
        if (!Proxy.getInstance().isOnlineOn2b2tForAtLeastDurationWithQueueSkip(sessionTimeLimit.minusMinutes(10))) return;
        if (lastWarningSent.isAfter(Instant.now().minus(Duration.ofMinutes(1)))) return;
        final ServerSession playerConnection = Proxy.getInstance().getCurrentPlayer().get();
        final Duration durationUntilKick = sessionTimeLimit.minus(Duration.ofSeconds(Proxy.getInstance().getOnlineTimeSecondsWithQueueSkip()));
        if (durationUntilKick.isNegative()) return; // sanity check just in case 2b's plugin changes
        debug("Sending 2b2t session time limit warning: {}m", durationUntilKick.toMinutes());
        lastWarningSent = Instant.now();
        var actionBarPacket = new ClientboundSetActionBarTextPacket(
            ComponentSerializer.minimessage((durationUntilKick.toMinutes() <= 3 ? "<red>" : "<blue>") + sessionTimeLimit.toHours() + "hr kick in: " + durationUntilKick.toMinutes() + "m"));
        playerConnection.sendAsync(actionBarPacket);
        // each packet will reset text render timer for 3 seconds
        for (int i = 1; i <= 7; i++) { // render the text for about 10 seconds total
            playerConnection.sendScheduledAsync(actionBarPacket, i, TimeUnit.SECONDS);
        }
        playerConnection.sendAsync(new ClientboundSoundPacket(
            BuiltinSound.BLOCK_ANVIL_PLACE,
            SoundCategory.AMBIENT,
            CACHE.getPlayerCache().getX(),
            CACHE.getPlayerCache().getY(),
            CACHE.getPlayerCache().getZ(),
            1.0f,
            1.0f + (ThreadLocalRandom.current().nextFloat() / 10f), // slight pitch variations
            0L
        ));
    }

    private void updateSessionTimeLimit() {
        if (!CONFIG.client.extra.sessionTimeLimit.dynamic2b2tSessionTimeLimit) return;
        if (lastUpdatedSessionTimeLimit.isAfter(Instant.now().minus(Duration.ofHours(1)))) return;
        try {
            sessionTimeLimit = Duration.ofHours(VcApi.INSTANCE.getSessionTimeLimit().orElseThrow().hours());
            lastUpdatedSessionTimeLimit = Instant.now();
            debug("Updated 2b2t session time limit: {}h", sessionTimeLimit);
        } catch (Exception e) {
            var nextWaitMinutes = ThreadLocalRandom.current().nextInt(1, 6);
            debug("Error updating 2b2t session time limit. Retrying in {} minutes", nextWaitMinutes, e);
            EXECUTOR.schedule(
                this::updateSessionTimeLimit,
                nextWaitMinutes,
                TimeUnit.MINUTES
            );
        }
    }

    public void refreshNow() {
        updateSessionTimeLimit();
    }
}
