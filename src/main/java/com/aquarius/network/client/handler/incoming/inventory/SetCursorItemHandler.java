package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;

import static com.aquarius.Globals.CACHE;

public class SetCursorItemHandler implements ClientEventLoopPacketHandler<ClientboundSetCursorItemPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetCursorItemPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().setMouseStack(packet.getContents());
        return true;
    }
}
