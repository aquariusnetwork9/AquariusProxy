package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.cache.data.entity.EntityStandard;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.MODULE;

public class COSetEntityMotionHandler implements PacketHandler<ClientboundSetEntityMotionPacket, ServerSession> {
    @Override
    public ClientboundSetEntityMotionPacket apply(final ClientboundSetEntityMotionPacket packet, final ServerSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        var coordObf = MODULE.get(CoordObfuscation.class);
        if (entity == null && !coordObf.getSpectatorEntityIds().contains(packet.getEntityId())) return null;
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }
        return packet;
    }
}
