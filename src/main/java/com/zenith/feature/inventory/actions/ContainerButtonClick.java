package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerButtonClickPacket;

@Data
public class ContainerButtonClick implements InventoryAction {
    private final int containerId;
    private final int buttonId;

    @Override
    public MinecraftPacket packet() {
        return new ServerboundContainerButtonClickPacket(containerId, buttonId);
    }

    @Override
    public int containerId() {
        return containerId;
    }
}
