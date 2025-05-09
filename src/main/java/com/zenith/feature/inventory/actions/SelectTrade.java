package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSelectTradePacket;
import org.jspecify.annotations.Nullable;

@Data
public class SelectTrade implements InventoryAction {
    private final int containerId;
    private final int slotId;

    @Override
    public int containerId() {
        return containerId;
    }

    @Override
    public @Nullable MinecraftPacket packet() {
        return new ServerboundSelectTradePacket(slotId);
    }
}
