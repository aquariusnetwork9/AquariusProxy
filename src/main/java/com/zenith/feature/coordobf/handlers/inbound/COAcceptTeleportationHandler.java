package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

import static com.zenith.Globals.MODULE;

public class COAcceptTeleportationHandler implements PacketHandler<ServerboundAcceptTeleportationPacket, ServerSession> {
    @Override
    public ServerboundAcceptTeleportationPacket apply(final ServerboundAcceptTeleportationPacket packet, final ServerSession session) {
        MODULE.get(CoordObfuscator.class).setPlayerAcceptTeleport(session, packet.getId());
        return packet;
    }
}
