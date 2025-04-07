package com.zenith.network.client.handler.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;

import static com.zenith.Globals.CACHE;

public class CGameProfileHandler implements PacketHandler<ClientboundGameProfilePacket, ClientSession> {
    @Override
    public ClientboundGameProfilePacket apply(final ClientboundGameProfilePacket packet, final ClientSession session) {
        CACHE.getProfileCache().setProfile(packet.getProfile());
        session.switchInboundState(ProtocolState.CONFIGURATION);
        session.send(new ServerboundLoginAcknowledgedPacket());
        session.switchOutboundState(ProtocolState.CONFIGURATION);
        return null;
    }
}
