package com.zenith.network.client.handler.incoming.level;

import com.zenith.Proxy;
import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;

public class ChunkBatchFinishedHandler implements PacketHandler<ClientboundChunkBatchFinishedPacket, ClientSession> {
    @Override
    public ClientboundChunkBatchFinishedPacket apply(final ClientboundChunkBatchFinishedPacket packet, final ClientSession session) {
        if (!Proxy.getInstance().hasActivePlayer()) {
            session.sendAsync(new ServerboundChunkBatchReceivedPacket(64)); // max allowed by server
        }
        return packet;
    }
}
