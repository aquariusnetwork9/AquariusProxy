package com.aquarius.command.api;

import com.aquarius.discord.Embed;
import com.aquarius.network.server.ServerSession;
import lombok.Data;
import lombok.experimental.Accessors;
import org.geysermc.mcprotocollib.auth.GameProfile;

import java.util.List;
import java.util.UUID;

import static com.aquarius.Globals.*;

@Data
@Accessors(fluent = true)
public class PlayerCommandSource implements CommandSource {
    private final String name;

    @Override
    public String name() {
        return name;
    }

    @Override
    public String commandPrefix() {
        return CONFIG.inGameCommands.slashCommands ? "/" : CONFIG.inGameCommands.prefix;
    }

    @Override
    public boolean validateAccountOwner(final CommandContext context) {
        final ServerSession currentPlayer = context.getInGamePlayerInfo().session();
        if (currentPlayer == null) return false;
        final GameProfile playerProfile = currentPlayer.getProfileCache().getProfile();
        if (playerProfile == null) return false;
        final UUID playerUUID = playerProfile.getId();
        if (playerUUID == null) return false;
        boolean allowed;
        if (CONFIG.inGameCommands.allowWhitelistedToUseAccountOwnerCommands) {
            allowed = PLAYER_LISTS.getWhitelist().contains(playerUUID);
        } else {
            final GameProfile proxyProfile = CACHE.getProfileCache().getProfile();
            if (proxyProfile == null) return false;
            final UUID proxyUUID = proxyProfile.getId();
            if (proxyUUID == null) return false;
            allowed = playerUUID.equals(proxyUUID); // we have to be logged in with the owning MC account
        }
        if (!allowed) {
            context.getEmbed()
                .addField("Error",
                          "Player: " + playerProfile.getName()
                              + " is not authorized to execute this command! "
                              + (CONFIG.inGameCommands.allowWhitelistedToUseAccountOwnerCommands ? "You must be whitelisted!" : "You must be logged in with the proxy's MC account!"),
                          false);
        }
        return allowed;
    }

    @Override
    public void logEmbed(final CommandContext ctx, final Embed embed) {
        CommandOutputHelper.logEmbedOutputToTerminal(embed);
        CommandOutputHelper.logEmbedOutputToInGame(embed, ctx.getInGamePlayerInfo().session());
    }

    @Override
    public void logMultiLine(final List<String> multiLine) {
        CommandOutputHelper.logMultiLineOutputToTerminal(multiLine);
    }
}
