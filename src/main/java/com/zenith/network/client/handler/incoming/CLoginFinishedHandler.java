package com.zenith.network.client.handler.incoming;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.util.BrandSerializer;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;

import static com.zenith.Globals.CACHE;

public class CLoginFinishedHandler implements PacketHandler<ClientboundLoginFinishedPacket, ClientSession> {
    @Override
    public ClientboundLoginFinishedPacket apply(final ClientboundLoginFinishedPacket packet, final ClientSession session) {
        CACHE.getProfileCache().setProfile(packet.getProfile());
        session.switchInboundState(ProtocolState.CONFIGURATION);
        session.send(new ServerboundLoginAcknowledgedPacket());
        session.switchOutboundState(ProtocolState.CONFIGURATION);
        session.send(new ServerboundCustomPayloadPacket(Key.key("minecraft", "brand"), BrandSerializer.serializeBrand("vanilla")));
        session.send(CACHE.getClientInfoCache().getClientInfoPacket());
        return null;
    }
}
