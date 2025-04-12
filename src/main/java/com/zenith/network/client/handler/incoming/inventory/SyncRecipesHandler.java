package com.zenith.network.client.handler.incoming.inventory;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateRecipesPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class SyncRecipesHandler implements ClientEventLoopPacketHandler<ClientboundUpdateRecipesPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundUpdateRecipesPacket packet, @NonNull ClientSession session) {
        CACHE.getRecipeCache().setRecipeRegistry(packet);
        return true;
    }
}
