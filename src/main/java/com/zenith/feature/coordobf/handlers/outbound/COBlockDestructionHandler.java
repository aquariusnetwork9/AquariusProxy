package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.player.World;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;

public class COBlockDestructionHandler implements PacketHandler<ClientboundBlockDestructionPacket, ServerSession> {
    @Override
    public ClientboundBlockDestructionPacket apply(final ClientboundBlockDestructionPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateBedrock) {
            int minY = World.getCurrentDimension().minY();
            if (packet.getY() <= minY + 5) {
                // cancel packet
                return null;
            }
            if (World.getCurrentDimension().id() == DimensionRegistry.THE_NETHER.id()) {
                if (packet.getY() >= 123) {
                    return null;
                }
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundBlockDestructionPacket(
            packet.getBreakerEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getStage()
        );
    }
}
