package com.zenith.network.server.handler.shared.incoming;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;

public class SClientInformationHandler implements PacketHandler<ServerboundClientInformationPacket, ServerSession> {
    public static final SClientInformationHandler INSTANCE = new SClientInformationHandler();
    @Override
    public ServerboundClientInformationPacket apply(final ServerboundClientInformationPacket packet, final ServerSession session) {
        session.getClientInfoCache()
            .setLocale(packet.getLocale())
            .setRenderDistance(packet.getRenderDistance())
            .setChatVisibility(packet.getChatVisibility())
            .setChatColors(packet.isUseChatColors())
            .setHandPreference(packet.getMainHand())
            .setTextFilteringEnabled(packet.isTextFilteringEnabled())
            .setAllowsListing(packet.isAllowsListing());
        return packet;
    }
}
