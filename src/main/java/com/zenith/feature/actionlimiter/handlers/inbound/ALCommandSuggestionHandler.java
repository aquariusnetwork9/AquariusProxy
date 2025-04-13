package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCommandSuggestionPacket;

import static com.zenith.Globals.CONFIG;

public class ALCommandSuggestionHandler implements PacketHandler<ServerboundCommandSuggestionPacket, ServerSession> {
    @Override
    public ServerboundCommandSuggestionPacket apply(final ServerboundCommandSuggestionPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowServerCommands) return packet;
        return null;
    }
}
