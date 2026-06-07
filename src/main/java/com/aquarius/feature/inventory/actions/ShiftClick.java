package com.aquarius.feature.inventory.actions;

import com.aquarius.cache.data.inventory.Container;
import com.aquarius.mc.item.ItemRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import java.util.Objects;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.CLIENT_LOG;

@Data
@RequiredArgsConstructor
public class ShiftClick implements InventoryAction {
    private final int containerId;
    private final int slotId;
    private final ShiftClickItemAction action;
    private static final ContainerActionType containerActionType = ContainerActionType.SHIFT_CLICK_ITEM;

    public ShiftClick(final int slotId, final ShiftClickItemAction action) {
        this(0, slotId, action);
    }

    @Override
    public MinecraftPacket packet() {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (!isStackEmpty(CACHE.getPlayerCache().getInventoryCache().getMouseStack())) {
            CLIENT_LOG.debug("Can't shift click, mouse stack is not empty: {}", this);
            return null;
        }
        var srcStack = container.getItemStack(slotId);
        if (isStackEmpty(srcStack)) {
            CLIENT_LOG.debug("Can't shift click, item stack is empty: {}", this);
            return null;
        }

        // Compute the quick-move destination + a real changed-slots prediction (the old code left it empty,
        // which anticheat servers like 2b2t/Grim reject - the item never moves). The item moves to the OTHER
        // section of the window: for an open container (id != 0) that's the container slots <-> the 36 player
        // slots; for the player inventory (id 0) it's main(9-35) <-> hotbar(36-44). We merge into matching
        // partial stacks first, then fill empty slots - matching vanilla AbstractContainerMenu.quickMoveStack.
        // handleContainerClick then applies these slots to the local cache (no reliance on a server resync).
        final int size = container.getSize();
        final int tgtStart, tgtEnd;
        if (containerId == 0) {
            if (slotId >= 36 && slotId <= 44) { tgtStart = 9; tgtEnd = 36; }     // hotbar -> main inventory
            else { tgtStart = 36; tgtEnd = 45; }                                 // elsewhere -> hotbar
        } else {
            final int playerStart = size - 36;
            if (slotId < playerStart) { tgtStart = playerStart; tgtEnd = size; } // container -> player
            else { tgtStart = 0; tgtEnd = playerStart; }                         // player -> container
        }

        final int maxStack = ItemRegistry.REGISTRY.get(srcStack.getId()).stackSize();
        int remaining = srcStack.getAmount();
        final Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectArrayMap<>();

        if (maxStack > 1) {                                                      // 1) merge into matching partial stacks
            for (int i = tgtStart; i < tgtEnd && remaining > 0; i++) {
                final var s = container.getItemStack(i);
                if (isStackEmpty(s) || s.getId() != srcStack.getId()
                    || !Objects.equals(s.getDataComponents(), srcStack.getDataComponents())
                    || s.getAmount() >= maxStack) continue;
                final int moved = Math.min(maxStack - s.getAmount(), remaining);
                changedSlots.put(i, new ItemStack(s.getId(), s.getAmount() + moved, s.getDataComponents()));
                remaining -= moved;
            }
        }
        for (int i = tgtStart; i < tgtEnd && remaining > 0; i++) {               // 2) fill empty slots
            if (!isStackEmpty(container.getItemStack(i))) continue;
            final int moved = Math.min(maxStack, remaining);
            changedSlots.put(i, new ItemStack(srcStack.getId(), moved, srcStack.getDataComponents()));
            remaining -= moved;
        }

        if (remaining == srcStack.getAmount()) {                                // nothing fit in the target section
            CLIENT_LOG.debug("Can't shift click, no room in target section: {}", this);
            return null;
        }
        changedSlots.put(slotId, remaining == 0                                 // 3) source slot emptied or reduced
            ? Container.EMPTY_STACK
            : new ItemStack(srcStack.getId(), remaining, srcStack.getDataComponents()));

        return new ServerboundContainerClickPacket(
            containerId,
            CONFIG.debug.inventoryRequestServerSyncOnAction
                ? CACHE.getPlayerCache().getActionId().get() + 1
                : CACHE.getPlayerCache().getActionId().get(),
            slotId,
            containerActionType,
            action,
            Container.EMPTY_STACK,
            changedSlots
        );
    }

    @Override
    public int containerId() {
        return containerId;
    }
}
