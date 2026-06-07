package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;

import static com.aquarius.Globals.CACHE;

public class RecipeBookAddHandler implements ClientEventLoopPacketHandler<ClientboundRecipeBookAddPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundRecipeBookAddPacket packet, final ClientSession session) {
        CACHE.getRecipeCache().addRecipeBookEntries(packet);
        return true;
    }
}
