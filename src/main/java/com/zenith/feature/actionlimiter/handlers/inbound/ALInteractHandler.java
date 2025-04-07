package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;

import static com.zenith.Globals.CONFIG;

public class ALInteractHandler implements PacketHandler<ServerboundInteractPacket, ServerSession> {
    @Override
    public ServerboundInteractPacket apply(final ServerboundInteractPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.allowInteract) return null;
        return packet;
    }
}
