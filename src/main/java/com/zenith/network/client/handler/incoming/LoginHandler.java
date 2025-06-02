package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.cache.CacheResetType;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatSessionUpdatePacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.*;
import static java.util.Arrays.asList;

public class LoginHandler implements PacketHandler<ClientboundLoginPacket, ClientSession> {
    @Override
    public ClientboundLoginPacket apply(@NonNull ClientboundLoginPacket packet, @NonNull ClientSession session) {
        CACHE.reset(CacheResetType.LOGIN);
        CACHE.getSectionCountProvider().updateDimension(packet.getCommonPlayerSpawnInfo());
        var serverProfile = CACHE.getProfileCache().getProfile();
        if (serverProfile == null) {
            CLIENT_LOG.warn("No server profile found, something has gone wrong. Using expected player UUID");
            CACHE.getProfileCache().setProfile(session.getPacketProtocol().getProfile());
        }
        CACHE.getPlayerCache()
            .setHardcore(packet.isHardcore())
            .setEntityId(packet.getEntityId())
            .setUuid(CACHE.getProfileCache().getProfile().getId())
            .setLastDeathPos(packet.getCommonPlayerSpawnInfo().getLastDeathPos())
            .setPortalCooldown(packet.getCommonPlayerSpawnInfo().getPortalCooldown())
            .setMaxPlayers(packet.getMaxPlayers())
            .setGameMode(packet.getCommonPlayerSpawnInfo().getGameMode())
            .setEnableRespawnScreen(packet.isEnableRespawnScreen())
            .setReducedDebugInfo(packet.isReducedDebugInfo());
        CACHE.getChunkCache().setWorldNames(asList(packet.getWorldNames()));
        CACHE.getChunkCache().setCurrentWorld(
            packet.getCommonPlayerSpawnInfo().getDimension(),
            packet.getCommonPlayerSpawnInfo().getWorldName(),
            packet.getCommonPlayerSpawnInfo().getHashedSeed(),
            packet.getCommonPlayerSpawnInfo().isDebug(),
            packet.getCommonPlayerSpawnInfo().isFlat()
        );
        CACHE.getChunkCache().setServerViewDistance(packet.getViewDistance());
        CACHE.getChunkCache().setServerSimulationDistance(packet.getSimulationDistance());
        if (CONFIG.client.chatSigning.enabled) {
            if (!packet.isEnforcesSecureChat() && CONFIG.client.chatSigning.force) {
                CLIENT_LOG.info("Force enabling chat signing even though server does not enforce it");
            }
            var useChatSigning = packet.isEnforcesSecureChat() || CONFIG.client.chatSigning.force;
            CACHE.getChatCache().setEnforcesSecureChat(useChatSigning);

            if (useChatSigning) {
                if (CACHE.getChatCache().canUseChatSigning()) {
                    var chatSession = CACHE.getChatCache().startNewChatSession();
                    session.sendAsync(new ServerboundChatSessionUpdatePacket(
                        chatSession.getSessionId(),
                        chatSession.getPlayerCertificates().getExpireTimeMs(),
                        chatSession.getPlayerCertificates().getPublicKey(),
                        chatSession.getPlayerCertificates().getPublicKeySignature()
                    ));
                    CLIENT_LOG.info("Server enforces secure chat, zenith chat signing enabled");
                } else {
                    CLIENT_LOG.warn("Server enforces secure chat, but we cannot sign chat messages");
                }
            } else {
                CLIENT_LOG.info("Server does not enforce secure chat, chat signing disabled");
            }
        } else {
            if (packet.isEnforcesSecureChat()) {
                CLIENT_LOG.warn("Server enforces secure chat, but zenith chat signing is disabled");
            }
            CACHE.getChatCache().setEnforcesSecureChat(packet.isEnforcesSecureChat());
        }

        if (!Proxy.getInstance().isOn2b2t()) {
            if (!session.isOnline()) {
                session.setOnline(true);
                EVENT_BUS.post(new ClientOnlineEvent());
            }
        }
        return packet;
    }
}
