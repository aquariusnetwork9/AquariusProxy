package com.zenith.network.client.handler.incoming.spawn;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import com.zenith.util.math.MutableVec3i;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;

import static com.zenith.Globals.CACHE;

public class SpawnPositionHandler implements ClientEventLoopPacketHandler<ClientboundSetDefaultSpawnPositionPacket, ClientSession> {
    @Override
    public boolean applyAsync(ClientboundSetDefaultSpawnPositionPacket packet, ClientSession session) {
        CACHE.getPlayerCache().setSpawnPosition(new MutableVec3i(packet.getX(), packet.getY(), packet.getZ()));
        return true;
    }
}
