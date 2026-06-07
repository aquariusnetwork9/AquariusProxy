package com.aquarius.network.client.handler.incoming;

import com.aquarius.Proxy;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;

import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;

public class SetEntityMotionHandler implements ClientEventLoopPacketHandler<ClientboundSetEntityMotionPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetEntityMotionPacket packet, final ClientSession session) {
        if (!Proxy.getInstance().hasActivePlayer() && packet.getEntityId() == CACHE.getPlayerCache().getEntityId()) {
            BOT.handleSetMotion(packet.getMotionX(), packet.getMotionY(), packet.getMotionZ());
        }
        return true;
    }
}
