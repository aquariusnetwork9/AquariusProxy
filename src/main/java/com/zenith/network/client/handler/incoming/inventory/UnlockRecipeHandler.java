package com.zenith.network.client.handler.incoming.inventory;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipePacket;

import static com.zenith.Globals.CACHE;

public class UnlockRecipeHandler implements ClientEventLoopPacketHandler<ClientboundRecipePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundRecipePacket packet, final ClientSession session) {
        CACHE.getRecipeCache().updateUnlockedRecipes(packet);
        return true;
    }
}
