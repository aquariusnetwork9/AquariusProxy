package com.aquarius.feature.actionlimiter.handlers.inbound;

import com.aquarius.feature.player.World;
import com.aquarius.mc.block.BlockRegistry;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.aquarius.Globals.CONFIG;

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
