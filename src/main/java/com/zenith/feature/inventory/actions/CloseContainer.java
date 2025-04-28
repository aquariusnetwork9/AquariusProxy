package com.zenith.feature.inventory.actions;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;

import static com.zenith.Globals.CACHE;

@Data
@RequiredArgsConstructor
public class CloseContainer implements InventoryAction {
    private final int containerId;

    public CloseContainer() {
        this(CACHE.getPlayerCache().getInventoryCache().getOpenContainerId());
    }

    @Override
    public MinecraftPacket packet() {
        return new ServerboundContainerClosePacket(containerId);
    }

    @Override
    public int containerId() {
        return containerId;
    }
}
