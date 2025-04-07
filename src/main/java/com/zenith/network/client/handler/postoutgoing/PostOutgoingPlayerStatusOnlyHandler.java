package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;

public class PostOutgoingPlayerStatusOnlyHandler implements ClientEventLoopPacketHandler<ServerboundMovePlayerStatusOnlyPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundMovePlayerStatusOnlyPacket packet, final ClientSession session) {
        // todo: cache onground
        return true;
    }
}
