package com.zenith.module;

import com.zenith.Proxy;
import com.zenith.command.util.CommandOutputHelper;
import com.zenith.discord.Embed;
import com.zenith.network.client.ClientSession;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;

import static com.zenith.Globals.DISCORD;
import static com.zenith.Globals.MODULE_LOG;

public abstract class ModuleUtils {
    public void sendClientPacketAsync(final Packet packet) {
        ClientSession clientSession = Proxy.getInstance().getClient();
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.sendAsync(packet);
        }
    }

    public void sendClientPacket(final Packet packet) {
        ClientSession clientSession = Proxy.getInstance().getClient();
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.send(packet);
        }
    }

    public void sendClientPacketAwait(final Packet packet) {
        ClientSession clientSession = Proxy.getInstance().getClient();
        if (clientSession != null && clientSession.isConnected()) {
            try {
                clientSession.sendAwait(packet);
            } catch (Exception e) {
                error("Error sending awaited packet: {}", packet.getClass().getSimpleName(), e);
            }
        }
    }

    // preserves packet order
    public void sendClientPacketsAsync(final Packet... packets) {
        ClientSession clientSession = Proxy.getInstance().getClient();
        if (clientSession != null && clientSession.isConnected()) {
            for (Packet packet : packets) {
                clientSession.sendAsync(packet);
            }
        }
    }

    protected final String moduleLogPrefix = "[" + this.getClass().getSimpleName() + "] ";

    public void debug(String msg) {
        MODULE_LOG.debug(moduleLogPrefix + msg);
    }

    public void debug(String msg, Object... args) {
        MODULE_LOG.debug(moduleLogPrefix + msg, args);
    }

    public void debug(Component msg) {
        MODULE_LOG.debug("{}{}", moduleLogPrefix, msg);
    }

    public void info(String msg) {
        MODULE_LOG.info(moduleLogPrefix + msg);
    }

    public void info(String msg, Object... args) {
        MODULE_LOG.info(moduleLogPrefix + msg, args);
    }

    public void info(Component msg) {
        MODULE_LOG.info("{}{}", moduleLogPrefix, msg);
    }

    public void warn(String msg) {
        MODULE_LOG.warn(moduleLogPrefix + msg);
    }

    public void warn(String msg, Object... args) {
        MODULE_LOG.warn(moduleLogPrefix + msg, args);
    }

    public void warn(Component msg) {
        MODULE_LOG.warn("{}{}", moduleLogPrefix, msg);
    }

    public void error(String msg) {
        MODULE_LOG.error(moduleLogPrefix + msg);
    }

    public void error(String msg, Object... args) {
        MODULE_LOG.error(moduleLogPrefix + msg, args);
    }

    public void error(Component msg) {
        MODULE_LOG.error("{}{}", moduleLogPrefix, msg);
    }

    protected final String moduleAlertPrefix = "<gray>[<aqua>" + this.getClass().getSimpleName() + "<gray>]<reset> ";

    public void inGameAlert(String minedown) {
        var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            var connection = connections[i];
            connection.sendAsyncAlert(moduleAlertPrefix + minedown);
        }
    }

    public void inGameAlertActivePlayer(String minedown) {
        var connection = Proxy.getInstance().getActivePlayer();
        if (connection == null) return;
        connection.sendAsyncAlert(moduleAlertPrefix + minedown);
    }

    // is also logged to the terminal
    public void discordNotification(Embed embed) {
        embed.title(moduleLogPrefix + (embed.isTitlePresent() ? embed.title() : ""));
        DISCORD.sendEmbedMessage(embed);
    }

    public void discordAndIngameNotification(Embed embed) {
        discordNotification(embed);
        CommandOutputHelper.logEmbedOutputToInGameAllConnectedPlayers(embed);
    }
}
