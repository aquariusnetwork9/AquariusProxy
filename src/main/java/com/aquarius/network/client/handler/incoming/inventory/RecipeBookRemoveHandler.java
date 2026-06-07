package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookRemovePacket;

import static com.aquarius.Globals.CACHE;

public class RecipeBookRemoveHandler implements ClientEventLoopPacketHandler<ClientboundRecipeBookRemovePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundRecipeBookRemovePacket packet, final ClientSession session) {
        CACHE.getRecipeCache().removeRecipeBookEntries(packet);
        return true;
    }
}
