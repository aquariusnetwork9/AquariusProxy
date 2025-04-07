package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPopPacket;

import static com.zenith.Globals.CACHE;

public class ResourcePackPopHandler implements PacketHandler<ClientboundResourcePackPopPacket, ClientSession> {
    public static final ResourcePackPopHandler INSTANCE = new ResourcePackPopHandler();
    @Override
    public ClientboundResourcePackPopPacket apply(final ClientboundResourcePackPopPacket packet, final ClientSession session) {
        if (packet.getId() == null) {
            CACHE.getConfigurationCache().getResourcePacks().clear();
        } else {
            CACHE.getConfigurationCache().getResourcePacks().remove(packet.getId());
        }
        return packet;
    }
}
