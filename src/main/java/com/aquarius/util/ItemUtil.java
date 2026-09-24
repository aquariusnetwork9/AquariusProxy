package com.aquarius.util;

import com.aquarius.mc.item.ItemRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class ItemUtil {
    private ItemUtil() {}

    /**
     * Reduce a real ItemStack to the id+count-only HashedStack that ServerboundContainerClickPacket has
     * carried since 1.21.6 ("hashed creative/click stacks" — the client asserts what it believes it holds
     * via a hash rather than the full stack; the server already knows the true stack and only compares).
     *
     * KNOWN LIMITATION: always reports "no non-default components" (empty added/removed sets) rather than
     * computing Mojang's real per-component hash, which is undocumented in this library and not worth
     * reverse-engineering blind. Correct for plain items (blocks, tools, un-enchanted anything — the
     * overwhelming majority of what a container click ever touches); an item with real custom components
     * (enchantments, custom names, written books) will report a hash mismatch, and vanilla servers resync
     * via a corrective SetSlot rather than reject the click outright — a mismatch here degrades gracefully,
     * it does not break the click.
     */
    public static @Nullable HashedStack toHashedStack(@Nullable ItemStack itemStack) {
        if (itemStack == null) return null;
        return new HashedStack(itemStack.getId(), itemStack.getAmount(), Map.of(), Set.of());
    }

    public static Int2ObjectMap<HashedStack> toHashedStacks(Int2ObjectMap<ItemStack> itemStacks) {
        Int2ObjectMap<HashedStack> hashedStacks = new Int2ObjectOpenHashMap<>(itemStacks.size());
        for (var entry : itemStacks.int2ObjectEntrySet()) {
            hashedStacks.put(entry.getIntKey(), toHashedStack(entry.getValue()));
        }
        return hashedStacks;
    }

    /**
     * The reverse of toHashedStack, for InventoryCache's own local click-prediction bookkeeping — the
     * ONLY place that reads a HashedStack back out of a click packet we just sent ourselves. Same
     * limitation in reverse: id + count survive, any real components (enchantments, custom data) do not,
     * since toHashedStack never captured them going out. A subsequent real ClientboundContainerSetContentPacket
     * from the server still corrects the slot fully — this is a best-effort prediction, not the final word.
     */
    public static @Nullable ItemStack toItemStack(@Nullable HashedStack hashedStack) {
        if (hashedStack == null) return null;
        return new ItemStack(hashedStack.id(), hashedStack.count());
    }

    public static int getDamageValue(ItemStack itemStack) {
        int defaultDamage = 0;
        if (itemStack == null) return defaultDamage;
        var dataComponents = itemStack.getDataComponentsOrEmpty();
        var damageComponent = dataComponents.get(DataComponentTypes.DAMAGE);
        if (damageComponent == null) return defaultDamage;
        return damageComponent;
    }

    public static int getMaxDamage(ItemStack itemStack) {
        int defaultMaxDamage = 0;
        if (itemStack == null) return defaultMaxDamage;
        var itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) return defaultMaxDamage;
        var defaultMaxDamageComponent = itemData.components().get(DataComponentTypes.MAX_DAMAGE);
        if (defaultMaxDamageComponent == null) return defaultMaxDamage;
        return defaultMaxDamageComponent;
    }

    public static int getDamageUntilBreak(ItemStack itemStack) {
        return getMaxDamage(itemStack) - getDamageValue(itemStack);
    }
}
