package com.zenith.network.server.handler.spectator.outgoing;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

public class PlayerPositionSpectatorOutgoingHandler implements PacketHandler<ClientboundPlayerPositionPacket, ServerSession> {
    @Override
    public ClientboundPlayerPositionPacket apply(ClientboundPlayerPositionPacket packet, ServerSession session) {
        if (session.isAllowSpectatorServerPlayerPosRotate()) {
            return packet;
        } else {
            return null;
        }
    }
}
