package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.TPS;

public class SetTimeHandler implements ClientEventLoopPacketHandler<ClientboundSetTimePacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundSetTimePacket packet, ClientSession session) {
        CACHE.getChunkCache().updateWorldTime(packet);
        TPS.handleTimeUpdate();
        return true;
    }
}
