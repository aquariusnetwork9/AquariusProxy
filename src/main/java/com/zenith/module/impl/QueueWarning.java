package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.api.event.module.QueueWarningEvent;
import com.zenith.api.event.queue.QueuePositionUpdateEvent;
import com.zenith.api.module.Module;
import com.zenith.discord.DiscordBot;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.EVENT_BUS;

public class QueueWarning extends Module {
    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(QueuePositionUpdateEvent.class, this::onQueuePositionUpdate)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.queueWarning.enabled;
    }

    private void onQueuePositionUpdate(QueuePositionUpdateEvent event) {
        if (CONFIG.client.extra.queueWarning.warningPositions.contains(event.position())) {
            var mention = CONFIG.client.extra.queueWarning.mentionPositions.contains(event.position());
            warn("Queue Position: " + DiscordBot.queuePositionStr());
            EVENT_BUS.postAsync(new QueueWarningEvent(event.position(), mention));
        }
    }
}
