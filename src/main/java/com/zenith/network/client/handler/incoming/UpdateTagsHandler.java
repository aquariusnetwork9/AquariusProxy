package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;

import static com.zenith.Globals.CACHE;

public class UpdateTagsHandler implements PacketHandler<ClientboundUpdateTagsPacket, ClientSession> {
    public static final UpdateTagsHandler INSTANCE = new UpdateTagsHandler();
    @Override
    public ClientboundUpdateTagsPacket apply(final ClientboundUpdateTagsPacket packet, final ClientSession session) {
        CACHE.getConfigurationCache().setTags(packet.getTags());
        return packet;
    }
}
