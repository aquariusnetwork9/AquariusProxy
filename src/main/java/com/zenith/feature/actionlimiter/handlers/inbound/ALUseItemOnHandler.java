package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockRegistry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.zenith.Globals.CONFIG;

public class ALUseItemOnHandler implements PacketHandler<ServerboundUseItemOnPacket, ServerSession> {
    @Override
    public ServerboundUseItemOnPacket apply(final ServerboundUseItemOnPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.allowUseItem) return null;
        if (!CONFIG.client.extra.actionLimiter.allowEnderChest) {
            var blockAtBlockPos = World.getBlock(packet.getX(), packet.getY(), packet.getZ());
            if (blockAtBlockPos.equals(BlockRegistry.ENDER_CHEST)) {
                return null;
            }
        }
        return packet;
    }
}
