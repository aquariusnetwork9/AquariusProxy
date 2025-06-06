package com.zenith.network.codec;

import com.zenith.network.client.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;

import java.util.concurrent.RejectedExecutionException;

import static com.zenith.Globals.CLIENT_LOG;

@FunctionalInterface
public interface ClientEventLoopPacketHandler<P extends Packet, S extends ClientSession> extends PacketHandler<P, S> {

    boolean applyAsync(P packet, S session);

    default P apply(P packet, S session) {
        if (packet == null) return null;
        try {
            session.executeInEventLoop(() -> applyCatching(packet, session));
        } catch (final RejectedExecutionException e) {
            // fall through
        }
        return packet;
    }

    private void applyCatching(P packet, S session) {
        try {
            if (!applyAsync(packet, session)) {
                CLIENT_LOG.debug("Client event loop packet handler failed: {}", packet.getClass().getSimpleName());
            }
        } catch (final Throwable e) {
            CLIENT_LOG.error("Client event loop packet handler error", e);
        }
    }
}
