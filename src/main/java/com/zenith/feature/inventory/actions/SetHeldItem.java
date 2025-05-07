package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CLIENT_LOG;

@Data
public class SetHeldItem implements InventoryAction {
    private final int hotbarSlotId;

    @Override
    public MinecraftPacket packet() {
        if (CACHE.getPlayerCache().getHeldItemSlot() == hotbarSlotId) {
            CLIENT_LOG.debug("[{}] Slot already held: {}", type(), hotbarSlotId);
            return null;
        }
        if (hotbarSlotId < 0 || hotbarSlotId > 8) {
            CLIENT_LOG.debug("[{}] Invalid slot ID: {}", type(), hotbarSlotId);
            return null;
        }
        return new ServerboundSetCarriedItemPacket(hotbarSlotId);
    }

    @Override
    public int containerId() {
        return 0;
    }
}
