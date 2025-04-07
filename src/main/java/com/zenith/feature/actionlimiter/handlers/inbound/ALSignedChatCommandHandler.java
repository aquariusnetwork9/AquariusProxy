package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;

import static com.zenith.Globals.CONFIG;

public class ALSignedChatCommandHandler implements PacketHandler<ServerboundChatCommandSignedPacket, ServerSession> {
    @Override
    public ServerboundChatCommandSignedPacket apply(final ServerboundChatCommandSignedPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowServerCommands) return packet;
        else return null;
    }
}
