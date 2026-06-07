package com.aquarius.network.client.handler.incoming;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import static com.aquarius.Globals.CACHE;

public class SetTimeHandler implements ClientEventLoopPacketHandler<ClientboundSetTimePacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundSetTimePacket packet, ClientSession session) {
        CACHE.getChunkCache().updateWorldTime(packet);
        return true;
    }
}
