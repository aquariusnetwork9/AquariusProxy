package com.zenith.network.client.handler.incoming.inventory;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;

import static com.zenith.Globals.CACHE;

public class ContainerOpenScreenHandler implements ClientEventLoopPacketHandler<ClientboundOpenScreenPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundOpenScreenPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().openContainer(packet.getContainerId(), packet.getType(), packet.getTitle());
        return true;
    }
}
