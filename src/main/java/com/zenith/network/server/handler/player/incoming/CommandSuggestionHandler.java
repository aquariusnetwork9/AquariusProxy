package com.zenith.network.server.handler.player.incoming;

import com.zenith.command.api.CommandSources;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandSuggestionsPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCommandSuggestionPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zenith.Globals.COMMAND;
import static com.zenith.Globals.CONFIG;

public class CommandSuggestionHandler implements PacketHandler<ServerboundCommandSuggestionPacket, ServerSession> {
    @Override
    public ServerboundCommandSuggestionPacket apply(final ServerboundCommandSuggestionPacket packet, final ServerSession session) {
        if (CONFIG.inGameCommands.enable && CONFIG.inGameCommands.slashCommands) {
            if (CONFIG.inGameCommands.suggestions) {
                var suggestions = COMMAND.getCommandSuggestions(packet.getText(), CommandSources.PLAYER);
                if (suggestions.isEmpty()) {
                    return packet;
                }
                final List<String> matches = new ArrayList<>();
                final List<Component> tooltips = new ArrayList<>();
                for (var s : suggestions.getList()) {
                    matches.add(s.getText());
                    tooltips.add(Optional.ofNullable(s.getTooltip())
                        .map(t -> Component.text(t.getString()))
                        .orElse(null)
                    );
                }
                var response = new ClientboundCommandSuggestionsPacket(
                    packet.getTransactionId(),
                    suggestions.getRange().getStart(),
                    suggestions.getRange().getLength(),
                    matches.toArray(new String[0]),
                    tooltips.toArray(new Component[0])
                );
                session.sendAsync(response);
                return null;
            }
            if (CONFIG.inGameCommands.slashCommandsReplacesServerCommands) {
                return null;
            }
        }
        return packet;
    }
}
