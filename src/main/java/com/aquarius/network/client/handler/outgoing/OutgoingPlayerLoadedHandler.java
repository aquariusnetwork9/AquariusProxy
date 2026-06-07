package com.aquarius.network.client.handler.outgoing;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

import static com.aquarius.Globals.CACHE;

public class OutgoingPlayerLoadedHandler implements PacketHandler<ServerboundPlayerLoadedPacket, ClientSession> {
    @Override
    public ServerboundPlayerLoadedPacket apply(final ServerboundPlayerLoadedPacket packet, final ClientSession session) {
        if (CACHE.getPlayerCache().isClientLoaded()) {
            return null;
        } else {
            CACHE.getPlayerCache().setClientLoaded(true);
            return packet;
        }
    }
}
