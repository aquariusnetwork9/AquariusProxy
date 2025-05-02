package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.zenith.Proxy;
import com.zenith.command.api.*;
import com.zenith.network.server.ServerSession;
import com.zenith.util.ComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;

import static com.zenith.Globals.EXECUTOR;
import static com.zenith.Globals.PLAYER_LISTS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SpectatorSwapCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("swap")
            .category(CommandCategory.MODULE)
            .description("""
            Swaps the current controlling player to spectator mode.
            """)
            .usageLines(
                "",
                "force"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("swap").requires(c -> Command.validateCommandSource(c, asList(CommandSources.PLAYER, CommandSources.SPECTATOR)))
            .executes(c -> {
                swap(c, false);
            })
            .then(literal("force").requires(Command::validateAccountOwner).executes(c -> {
                swap(c, true);
            }));
    }

    private void swap(com.mojang.brigadier.context.CommandContext<CommandContext> c, boolean force) {
        ServerSession activePlayer = Proxy.getInstance().getActivePlayer();
        if (c.getSource().getSource() == CommandSources.PLAYER) {
            var player = activePlayer;
            if (player == null) {
                c.getSource().getEmbed()
                    .title("Unable to Swap")
                    .errorColor()
                    .description("No player is currently controlling the proxy account");
                return;
            }
            if (player.getProtocolVersion().olderThan(ProtocolVersion.v1_20_5)) {
                c.getSource().getEmbed()
                    .title("Unsupported Client MC Version")
                    .errorColor()
                    .addField("Client Version", player.getProtocolVersion().getName(), false)
                    .addField("Error", "Client version must be at least 1.20.6", false);
                return;
            }
            player.transferToSpectator();
        } else if (c.getSource().getSource() == CommandSources.SPECTATOR) {
            var session = c.getSource().getInGamePlayerInfo().session();
            var spectatorProfile = session.getProfileCache().getProfile();
            c.getSource().setNoOutput(true);
            if (spectatorProfile == null) return;
            if (!PLAYER_LISTS.getWhitelist().contains(spectatorProfile.getId())) {
                session.send(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<red>You are not whitelisted!"), false));
                return;
            }
            if (session.getProtocolVersion().olderThan(ProtocolVersion.v1_20_5)) {
                session.send(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<red>Unsupported Client MC Version"), false));
                return;
            }
            if (activePlayer != null) {
                if (force) {
                    if (activePlayer.getProtocolVersion().olderThan(ProtocolVersion.v1_20_5)) {
                        session.send(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<red>Controlling player is using an unsupported Client MC Version"), false));
                        return;
                    }
                    activePlayer.transferToSpectator();
                    EXECUTOR.schedule(() -> session.transferToControllingPlayer(), 1, SECONDS);
                } else {
                    session.send(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<red>Someone is already controlling the player!"), false));
                }
                return;
            }
            session.transferToControllingPlayer();
        }
    }
}
