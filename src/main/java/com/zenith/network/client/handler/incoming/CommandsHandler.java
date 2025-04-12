package com.zenith.network.client.handler.incoming;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;

import static com.zenith.Globals.CACHE;

public class CommandsHandler implements ClientEventLoopPacketHandler<ClientboundCommandsPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundCommandsPacket packet, final ClientSession session) {
        CACHE.getChatCache().setCommandNodes(packet.getNodes());
        CACHE.getChatCache().setFirstCommandNodeIndex(packet.getFirstNodeIndex());
        return true;
    }
}
