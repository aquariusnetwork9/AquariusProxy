package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

import static com.zenith.Globals.CACHE;

public class COUseItemHandler implements PacketHandler<ServerboundUseItemPacket, ServerSession> {
    @Override
    public ServerboundUseItemPacket apply(final ServerboundUseItemPacket packet, final ServerSession session) {
        try {
            // its still possible to throw an eye of ender even with this handler
            // cache is updated on the client event loop
            // and player could buffer this use packet right after a container slot swap in one tick
            // so this will only block the simplest case
            ItemStack itemStack = CACHE.getPlayerCache().getEquipment(packet.getHand());
            if (itemStack != Container.EMPTY_STACK && itemStack.getId() == ItemRegistry.ENDER_EYE.id()) {
                return null;
            }
        } catch (final Exception e) {
            return null;
        }
        return packet;
    }
}
