package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.api.event.client.ClientDisconnectEvent;
import com.zenith.api.event.queue.QueueStartEvent;
import com.zenith.api.module.Module;
import com.zenith.api.network.PacketHandlerCodec;
import com.zenith.api.network.PacketHandlerStateCodec;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.EXECUTOR;

/**
 * On 2b2t, if we stop responding to KeepAlive packets we will get sent back to queue
 * not to the end of the queue, but as if we were doing a reconnect queue skip
 * so this can be useful for resetting the player state without having to do a full reconnect
 */
public class Requeue extends Module {

    @Nullable ScheduledFuture<?> requeueTickFuture = null;
    Timer requeueTimer = Timers.timer();

    @Override
    public boolean enabledSetting() {
        return false;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(QueueStartEvent.class, e -> disable()),
            of(ClientDisconnectEvent.class, e -> disable())
        );
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("requeue")
            .setPriority(10)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .registerOutbound(ServerboundKeepAlivePacket.class, (packet, session) -> null)
                .build())
            .build();
    }

    @Override
    public void onEnable() {
        requeueTimer.reset();
        requeueTickFuture = EXECUTOR.scheduleWithFixedDelay(this::requeueTick, 0, 1, TimeUnit.SECONDS);
    }


    private void requeueTick() {
        if (!Proxy.getInstance().isConnected()
            || Proxy.getInstance().isInQueue()
            || requeueTimer.tick(TimeUnit.SECONDS.toMillis(60))
        ) {
            disable();
        }
    }

    @Override
    public void onDisable() {
        if (requeueTickFuture != null) {
            requeueTickFuture.cancel(true);
        }
        info("Requeue Completed");
        inGameAlert("Requeue Completed");
    }
}
