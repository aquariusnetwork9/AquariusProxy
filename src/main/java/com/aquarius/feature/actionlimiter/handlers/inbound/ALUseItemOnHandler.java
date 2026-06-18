package com.aquarius.feature.actionlimiter.handlers.inbound;

import com.aquarius.feature.player.World;
import com.aquarius.mc.block.BlockRegistry;
import com.aquarius.module.impl.ActionLimiter;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class ALUseItemOnHandler implements PacketHandler<ServerboundUseItemOnPacket, ServerSession> {
    @Override
    public ServerboundUseItemOnPacket apply(final ServerboundUseItemOnPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.allowUseItem) return null;
        // Blacklisted items may be held but not used/placed (e.g. hold an ender crystal, can't place it).
        if (CONFIG.client.extra.actionLimiter.itemsBlacklistEnabled
            && MODULE.get(ActionLimiter.class).isBlacklistedItem(CACHE.getPlayerCache().getEquipment(packet.getHand()))) {
            return null;
        }
        if (!CONFIG.client.extra.actionLimiter.allowEnderChest) {
            var blockAtBlockPos = World.getBlock(packet.getX(), packet.getY(), packet.getZ());
            if (blockAtBlockPos.equals(BlockRegistry.ENDER_CHEST)) {
                return null;
            }
        }
        return packet;
    }
}
