package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.queue.QueuePositionUpdateEvent;
import com.zenith.event.server.ServerPlayerConnectedEvent;
import com.zenith.event.server.ServerPlayerDisconnectedEvent;
import com.zenith.feature.extrachat.ECChatCommandIncomingHandler;
import com.zenith.feature.extrachat.ECPlayerChatOutgoingHandler;
import com.zenith.feature.extrachat.ECSignedChatCommandIncomingHandler;
import com.zenith.feature.extrachat.ECSystemChatOutgoingHandler;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CHAT_LOG;
import static com.zenith.Globals.CONFIG;
import static java.util.Objects.nonNull;

public class ExtraChat extends Module {

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.chat.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ServerPlayerConnectedEvent.class, this::handleServerPlayerConnected),
            of(ServerPlayerDisconnectedEvent.class, this::handleServerPlayerDisconnected),
            of(QueuePositionUpdateEvent.class, this::handleQueuePositionUpdate)
        );
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("extra-chat")
            .setPriority(-1)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .outbound(ClientboundSystemChatPacket.class, new ECSystemChatOutgoingHandler())
                .outbound(ClientboundPlayerChatPacket.class, new ECPlayerChatOutgoingHandler())
                .inbound(ServerboundChatCommandPacket.class, new ECChatCommandIncomingHandler())
                .inbound(ServerboundChatCommandSignedPacket.class, new ECSignedChatCommandIncomingHandler())
                .build())
            .build();
    }

    private void handleServerPlayerDisconnected(ServerPlayerDisconnectedEvent event) {
        if (!CONFIG.client.extra.chat.showConnectionMessages) return;
        var serverConnection = Proxy.getInstance().getCurrentPlayer().get();
        if (nonNull(serverConnection) && serverConnection.isLoggedIn())
            serverConnection.sendAsync(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<aqua>" + event.playerEntry().getName() + "<yellow> disconnected"), false));
    }

    private void handleServerPlayerConnected(ServerPlayerConnectedEvent event) {
        if (!CONFIG.client.extra.chat.showConnectionMessages) return;
        var serverConnection = Proxy.getInstance().getCurrentPlayer().get();
        if (nonNull(serverConnection) && serverConnection.isLoggedIn())
            serverConnection.sendAsync(new ClientboundSystemChatPacket(ComponentSerializer.minimessage("<aqua>" + event.playerEntry().getName() + "<yellow> connected"), false));
    }

    private void handleQueuePositionUpdate(QueuePositionUpdateEvent event) {
        if (!CONFIG.client.extra.logChatMessages || !CONFIG.client.extra.logOnlyQueuePositionUpdates) return;
        CHAT_LOG.info(Component.text("Position in queue: " + event.position()).color(NamedTextColor.GOLD));
    }
}
