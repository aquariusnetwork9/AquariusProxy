package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatSessionUpdatePacket;

import static com.aquarius.Globals.CONFIG;

public class SChatSessionUpdateHandler implements PacketHandler<ServerboundChatSessionUpdatePacket, ServerSession> {
    @Override
    public ServerboundChatSessionUpdatePacket apply(final ServerboundChatSessionUpdatePacket packet, final ServerSession session) {
        return switch (CONFIG.server.chatSigning.mode) {
            case PASSTHROUGH -> packet;
            case DISGUISED, SYSTEM -> null;
        };
    }
}
