package com.aquarius.network.client.handler.incoming;

import com.aquarius.Proxy;
import com.aquarius.cache.data.PlayerCache;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

public class CKeepAliveHandler implements PacketHandler<ClientboundKeepAlivePacket, ClientSession> {
    public static final CKeepAliveHandler INSTANCE = new CKeepAliveHandler();
    @Override
    public ClientboundKeepAlivePacket apply(final ClientboundKeepAlivePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getKeepAliveQueue().add(new PlayerCache.KeepAliveRequest(System.nanoTime(), packet.getPingId()));
        return switch (CONFIG.client.keepAliveHandling.keepAliveMode) {
            case INDEPENDENT -> {
                session.sendAsync(new ServerboundKeepAlivePacket(packet.getPingId()));
                yield null;
            }
            case PASSTHROUGH -> {
                if (Proxy.getInstance().hasActivePlayer()) {
                    yield packet;
                } else {
                    session.sendAsync(new ServerboundKeepAlivePacket(packet.getPingId()));
                    yield null;
                }
            }
        };
    }
}
