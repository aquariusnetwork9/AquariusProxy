package com.aquarius.feature.actionlimiter.handlers.outbound;

import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandSuggestionsPacket;

import static com.aquarius.Globals.CONFIG;

public class ALCCommandSuggestionsHandler implements PacketHandler<ClientboundCommandSuggestionsPacket, ServerSession> {
    @Override
    public ClientboundCommandSuggestionsPacket apply(final ClientboundCommandSuggestionsPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowServerCommands) return packet;
        return null;
    }
}
