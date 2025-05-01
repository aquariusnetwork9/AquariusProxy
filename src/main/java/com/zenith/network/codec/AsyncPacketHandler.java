package com.zenith.network.codec;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;

import static com.zenith.Globals.SERVER_LOG;

@FunctionalInterface
public interface AsyncPacketHandler<P extends Packet, S extends Session> extends PacketHandler<P, S> {
    EventLoop EVENT_LOOP = new DefaultEventLoop(new DefaultThreadFactory("ZenithProxy Async Packet Handler", true));

    boolean applyAsync(P packet, S session);

    default P apply(P packet, S session) {
        if (packet == null) return null;
        EVENT_LOOP.execute(() -> applyCatching(packet, session));
        return packet;
    }

    private void applyCatching(P packet, S session) {
        try {
            if (!applyAsync(packet, session)) {
                SERVER_LOG.warn("Async packet handler failed: {}", packet.getClass().getSimpleName());
            }
        } catch (final Throwable e) {
            SERVER_LOG.error("Async packet handler error", e);
        }
    }
}
