package com.aquarius.network.client.handler.incoming;

import com.aquarius.event.chat.PublicChatEvent;
import com.aquarius.event.chat.WhisperChatEvent;
import com.aquarius.mc.chat_type.ChatType;
import com.aquarius.mc.chat_type.ChatTypeRegistry;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;

import java.util.Optional;

import static com.aquarius.Globals.*;

public class DisguisedChatHandler implements PacketHandler<ClientboundDisguisedChatPacket, ClientSession> {

    @Override
    public ClientboundDisguisedChatPacket apply(final ClientboundDisguisedChatPacket packet, final ClientSession session) {
        var senderPlayerEntry = CACHE.getTabListCache().getFromName(ComponentSerializer.serializePlain(packet.getName()));
        ChatType chatType = ChatTypeRegistry.REGISTRY.get(packet.getChatType().id());
        if (chatType != null) {
            Component chatComponent = chatType.render(
                packet.getName(),
                packet.getMessage(),
                null,
                packet.getTargetName());
            if (CONFIG.client.extra.logChatMessages) {
                CHAT_LOG.info(chatComponent);
            }
            String messageContent = ComponentSerializer.serializePlain(packet.getMessage());
            boolean isWhisper = false;
            Optional<PlayerListEntry> whisperTarget = Optional.empty();
            if ("commands.message.display.incoming".equals(chatType.translationKey())) {
                isWhisper = true;
                whisperTarget = CACHE.getTabListCache().get(CACHE.getProfileCache().getProfile().getId());
            } else if ("commands.message.display.outgoing".equals(chatType.translationKey())) {
                isWhisper = true;
                whisperTarget = CACHE.getTabListCache().getFromName( // ???
                     ComponentSerializer.serializePlain(packet.getTargetName())
                );
            }
            if (isWhisper) {
                if (senderPlayerEntry.isEmpty()) {
                    CLIENT_LOG.warn("No sender found for PlayerChatPacket whisper. chatType: {}, content: {}", chatType.translationKey(), ComponentSerializer.serializePlain(chatComponent));
                } else if (whisperTarget.isEmpty()) {
                    CLIENT_LOG.warn("No whisper target found for PlayerChatPacket whisper. chatType: {}, content: {}", chatType.translationKey(), ComponentSerializer.serializePlain(chatComponent));
                } else {
                    boolean outgoing = "commands.message.display.outgoing".equals(chatType.translationKey());
                    EVENT_BUS.postAsync(new WhisperChatEvent(
                        outgoing,
                        senderPlayerEntry.get(),
                        whisperTarget.get(),
                        chatComponent,
                        messageContent));
                }
            } else {
                if (senderPlayerEntry.isEmpty()) {
                    CLIENT_LOG.warn("No sender found for PlayerChatPacket public chat. chatType: {}, content: {}", chatType.translationKey(), ComponentSerializer.serializePlain(chatComponent));
                } else {
                    EVENT_BUS.postAsync(new PublicChatEvent(
                        senderPlayerEntry.get(),
                        chatComponent,
                        messageContent
                    ));
                }
            }
        } else {
            CLIENT_LOG.warn("Unknown chat type: {}", packet.getChatType().id());
        }
        return packet;
    }
}
