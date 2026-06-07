package com.aquarius.feature.actionlimiter.handlers.inbound;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import static com.aquarius.Globals.CONFIG;

public class ALClientCommandHandler implements PacketHandler<ServerboundClientCommandPacket, ServerSession> {
    @Override
    public ServerboundClientCommandPacket apply(final ServerboundClientCommandPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowRespawn) return packet;
        session.disconnect("ActionLimiter: Respawn not allowed");
        return null;
    }
}
