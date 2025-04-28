package com.zenith.feature.inventory.actions;

import com.zenith.cache.data.inventory.Container;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CLIENT_LOG;

@Data
@RequiredArgsConstructor
public class MoveToHotbarSlot implements InventoryAction {
    private final int containerId;
    private final int slotId;
    private final ContainerActionType actionType = ContainerActionType.MOVE_TO_HOTBAR_SLOT;
    private final MoveToHotbarAction moveToHotbarAction;

    public MoveToHotbarSlot(final int slotId, final MoveToHotbarAction moveToHotbarAction) {
        this(0, slotId, moveToHotbarAction);
    }

    @Override
    public MinecraftPacket packet() {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        var mouseStack = CACHE.getPlayerCache().getInventoryCache().getMouseStack();
        if (!isStackEmpty(mouseStack)) {
            CLIENT_LOG.debug("[{}, {}, {}] Can't move to hotbar, mouse stack is not empty", slotId, actionType, moveToHotbarAction);
            return null; // can't swap if mouse stack is not empty
        }
        final ItemStack clickStack = container.getItemStack(slotId);
        if (isStackEmpty(clickStack)) {
            CLIENT_LOG.debug("[{}, {}, {}] Can't swap empty stack", slotId, actionType, moveToHotbarAction);
            return null; // can't swap if clickStack is empty
        }
        Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectArrayMap<>();
        int hotBarSlot = -1;
        boolean playerInv = containerId == 0;
        int hotbarOffset = playerInv ? 36 : container.getSize() - 9;
        switch (moveToHotbarAction) {
            case SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7, SLOT_8, SLOT_9 -> {// swap the clickStack with the item in the hotbar slot
                hotBarSlot = moveToHotbarAction.getId() + hotbarOffset;
            }
            case OFF_HAND -> {
                if (playerInv) hotBarSlot = 45;
            }
            default -> {
                CLIENT_LOG.debug("[{}, {}, {}] Unhandled action param", slotId, actionType, moveToHotbarAction);
                return null;
            }
        }
        if (hotBarSlot != -1) {
            final ItemStack swapStack = container.getItemStack(hotBarSlot);
            changedSlots.put(hotBarSlot, clickStack);
            changedSlots.put(slotId, swapStack);
        } else {
            // there is no offhand slot id in the container, so only one slot is set as changed in the packet
            var offhandStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.OFF_HAND);
            changedSlots.put(slotId, offhandStack);
        }


        return new ServerboundContainerClickPacket(
            containerId,
            CACHE.getPlayerCache().getActionId().incrementAndGet(),
            slotId,
            actionType,
            moveToHotbarAction,
            Container.EMPTY_STACK,
            changedSlots
        );
    }

    @Override
    public int containerId() {
        return containerId;
    }
}
