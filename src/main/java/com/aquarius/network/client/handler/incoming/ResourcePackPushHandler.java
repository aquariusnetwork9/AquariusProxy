package com.aquarius.network.client.handler.incoming;

import com.aquarius.Proxy;
import com.aquarius.cache.data.config.ResourcePack;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

public class ResourcePackPushHandler implements PacketHandler<ClientboundResourcePackPushPacket, ClientSession> {
    public static final ResourcePackPushHandler INSTANCE = new ResourcePackPushHandler();
    @Override
    public ClientboundResourcePackPushPacket apply(final ClientboundResourcePackPushPacket packet, final ClientSession session) {
        CACHE.getConfigurationCache().getResourcePacks().put(packet.getId(), new ResourcePack(packet.getId(), packet.getUrl(), packet.getHash(), packet.isRequired(), packet.getPrompt()));
        if (Proxy.getInstance().hasActivePlayer() && CONFIG.debug.passthroughResourcePacks) return packet;
        else {
            session.sendAsync(new ServerboundResourcePackPacket(packet.getId(), ResourcePackStatus.ACCEPTED));
            session.sendAsync(new ServerboundResourcePackPacket(packet.getId(), ResourcePackStatus.DOWNLOADED));
            session.sendAsync(new ServerboundResourcePackPacket(packet.getId(), ResourcePackStatus.SUCCESSFULLY_LOADED));
            return null;
        }
    }
}
