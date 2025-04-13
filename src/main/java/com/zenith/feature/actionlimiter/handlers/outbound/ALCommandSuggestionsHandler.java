package com.zenith.feature.actionlimiter.handlers.outbound;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandSuggestionsPacket;

import static com.zenith.Globals.CONFIG;

public class ALCommandSuggestionsHandler implements PacketHandler<ClientboundCommandSuggestionsPacket, ServerSession> {
    @Override
    public ClientboundCommandSuggestionsPacket apply(final ClientboundCommandSuggestionsPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowServerCommands) return packet;
        return null;
    }
}
