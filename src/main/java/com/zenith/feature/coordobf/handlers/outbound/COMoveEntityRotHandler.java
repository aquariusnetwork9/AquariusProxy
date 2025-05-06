package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;

public class COMoveEntityRotHandler implements PacketHandler<ClientboundMoveEntityRotPacket, ServerSession> {
    @Override
    public ClientboundMoveEntityRotPacket apply(final ClientboundMoveEntityRotPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity == null && !coordObf.getSpectatorEntityIds().contains(packet.getEntityId())) return null;
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }
        return packet;
    }
}
