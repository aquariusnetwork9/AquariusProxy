package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.PostOutgoingPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;

import static com.zenith.Globals.CACHE;

public class PostOutgoingClientInformationHandler implements PostOutgoingPacketHandler<ServerboundClientInformationPacket, ClientSession> {
    public static final PostOutgoingClientInformationHandler INSTANCE = new PostOutgoingClientInformationHandler();

    @Override
    public void accept(final ServerboundClientInformationPacket packet, final ClientSession session) {
        CACHE.getChunkCache().setRenderDistance(packet.getRenderDistance());
    }
}
