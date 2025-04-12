package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.*;
import com.zenith.discord.DiscordBot;
import com.zenith.discord.Embed;
import com.zenith.feature.api.sessionserver.SessionServerApi;
import com.zenith.feature.player.World;
import com.zenith.feature.whitelist.PlayerListsManager;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;

import java.util.EnumSet;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EXECUTOR;
import static java.util.Arrays.asList;

public class SkinCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("skin")
            .category(CommandCategory.MODULE)
            .description("""
            Temporarily change your skin to another player's skin.
            
            This is only client-side and only affects how you see yourself.
            """)
            .usageLines(
                "<playerName>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("skin").requires(c -> Command.validateCommandSource(c, CommandSource.IN_GAME_PLAYER))
            .then(argument("playerName", word()).executes(c -> {
                var playerName = c.getArgument("playerName", String.class);
                c.getSource().setNoOutput(true);
                var session = Proxy.getInstance().getActivePlayer();
                if (session == null) {
                    // shouldn't ever get here
                    return ERROR;
                }
                EXECUTOR.execute(() -> updateSkin(session, playerName));
                return OK;
            }));
    }

    private void updateSkin(final ServerSession session, final String playerName) {
        PlayerListsManager.getProfileFromUsername(playerName)
            .flatMap(profile -> SessionServerApi.INSTANCE.getProfileAndSkin(profile.uuid()))
            .ifPresentOrElse(profile -> {
                var existingProfile = CACHE.getProfileCache().getProfile();
                if (existingProfile == null) {
                    var embed = Embed.builder()
                        .title("Error")
                        .description("Failed to get current profile");
                    CommandOutputHelper.logEmbedOutputToInGame(embed, session);
                    return;
                }
                var newProfile = new GameProfile(existingProfile.getId(), existingProfile.getName());
                newProfile.setProperties(profile.getProperties());
                var existingEntryOptional = CACHE.getTabListCache().get(existingProfile.getId());
                if (existingEntryOptional.isEmpty()) {
                    var embed = Embed.builder()
                        .title("Error")
                        .description("Failed to get existing tab list entry");
                    CommandOutputHelper.logEmbedOutputToInGame(embed, session);
                    return;
                }
                var existingEntry = existingEntryOptional.get();
                session.sendAsync(new ClientboundPlayerInfoRemovePacket(asList(existingProfile.getId())));
                session.sendAsync(new ClientboundPlayerInfoUpdatePacket(EnumSet.allOf(PlayerListEntryAction.class), new PlayerListEntry[]{
                    new PlayerListEntry(newProfile.getId(), newProfile, existingEntry.isListed(), existingEntry.getLatency(), existingEntry.getGameMode(), existingEntry.getDisplayName(), existingEntry.getSessionId(), existingEntry.getExpiresAt(), existingEntry.getPublicKey(), existingEntry.getKeySignature())
                }));
                session.sendAsync(new ClientboundRespawnPacket(
                    new PlayerSpawnInfo(
                        World.getCurrentDimension().id(),
                        CACHE.getChunkCache().getWorldName(),
                        CACHE.getChunkCache().getHashedSeed(),
                        CACHE.getPlayerCache().getGameMode(),
                        CACHE.getPlayerCache().getGameMode(),
                        CACHE.getChunkCache().isDebug(),
                        CACHE.getChunkCache().isFlat(),
                        CACHE.getPlayerCache().getLastDeathPos(),
                        CACHE.getPlayerCache().getPortalCooldown()
                    ),
                    true,
                    true
                ));
                CACHE.getPlayerCache().getPackets(session::sendAsync, session);
                CACHE.getChunkCache().getPackets(session::sendAsync, session);
                var embed = Embed.builder()
                    .title("Skin Changed!")
                    .addField("Skin Owner", DiscordBot.escape(playerName), false)
                    .primaryColor();
                CommandOutputHelper.logEmbedOutputToInGame(embed, session);
            }, () -> {
                var embed = Embed.builder()
                    .title("Error")
                    .description("Failed to get skin for: " + DiscordBot.escape(playerName));
                CommandOutputHelper.logEmbedOutputToInGame(embed, session);
            });
    }
}
