package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

import static com.zenith.Shared.CACHE;

public class COUseItemHandler implements PacketHandler<ServerboundUseItemPacket, ServerSession> {
    @Override
    public ServerboundUseItemPacket apply(final ServerboundUseItemPacket packet, final ServerSession session) {
        // todo: move this to action limiter
        int slot = 36;
        if (packet.getHand() == Hand.MAIN_HAND) {
            int heldItemSlot = CACHE.getPlayerCache().getHeldItemSlot();
            slot = 36 + heldItemSlot;
        } else if (packet.getHand() == Hand.OFF_HAND) {
            slot = 45;
        }
        try {
            ItemStack itemStack = CACHE.getPlayerCache().getPlayerInventory().get(slot);
            if (itemStack.getId() == ItemRegistry.ENDER_EYE.id()) {
                return null;
            }
        } catch (final Exception e) {
            return null;
        }
        return packet;
    }
}
