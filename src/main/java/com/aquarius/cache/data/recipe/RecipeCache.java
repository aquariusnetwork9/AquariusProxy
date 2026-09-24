package com.aquarius.cache.data.recipe;

import com.aquarius.cache.CacheResetType;
import com.aquarius.cache.CachedData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Data;
import lombok.experimental.Accessors;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpSession;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookSettingsPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateRecipesPacket;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Data
@Accessors(chain = true)
public class RecipeCache implements CachedData {
    /** Closed and unfiltered — vanilla's own state before any ClientboundRecipeBookSettingsPacket arrives. */
    private static final ClientboundRecipeBookSettingsPacket.TypeSettings DEFAULT_TYPE_SETTINGS =
        new ClientboundRecipeBookSettingsPacket.TypeSettings(false, false);
    protected Map<String, int[]> itemSets = new ConcurrentHashMap<>();
    protected Set<ClientboundUpdateRecipesPacket.SelectableRecipe> stoneCutterRecipes = Collections.synchronizedSet(new ObjectOpenHashSet<>());
    /**
     * The 4 recipe-book categories the server tracks (crafting/furnace/blastFurnace/smoker), each with its
     * own open/filtered state. Was a Map<CraftingBookStateType, TypeSettings> before 1.21.11 removed that
     * enum and flattened the packet to 4 named fields; kept as a map here (String key = the field name) so
     * the rest of this class's shape — and reset()'s single Map.clear() — did not need to change.
     */
    protected Map<String, ClientboundRecipeBookSettingsPacket.TypeSettings> recipeBookSettings = Collections.synchronizedMap(new LinkedHashMap<>());
    protected Int2ObjectMap<RecipeDisplayEntry> recipeBookEntries = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    @Override
    public synchronized void getPackets(@NonNull final Consumer<Packet> consumer, final @NonNull TcpSession session) {
        consumer.accept(new ClientboundUpdateRecipesPacket(itemSets, new ArrayList<>(stoneCutterRecipes)));
        consumer.accept(new ClientboundRecipeBookSettingsPacket(
            recipeBookSettings.getOrDefault("crafting", DEFAULT_TYPE_SETTINGS),
            recipeBookSettings.getOrDefault("furnace", DEFAULT_TYPE_SETTINGS),
            recipeBookSettings.getOrDefault("blastFurnace", DEFAULT_TYPE_SETTINGS),
            recipeBookSettings.getOrDefault("smoker", DEFAULT_TYPE_SETTINGS)));
        final List<ClientboundRecipeBookAddPacket.Entry> entries = new ArrayList<>(recipeBookEntries.size());
        for (var entry : recipeBookEntries.int2ObjectEntrySet()) {
            entries.add(new ClientboundRecipeBookAddPacket.Entry(entry.getValue(), false, false));
        }
        consumer.accept(new ClientboundRecipeBookAddPacket(entries, true));
    }

    @Override
    public synchronized void reset(CacheResetType type) {
        if (type == CacheResetType.FULL || type == CacheResetType.PROTOCOL_SWITCH) {
            this.itemSets.clear();
            this.stoneCutterRecipes.clear();
            this.recipeBookSettings.clear();
            this.recipeBookEntries.clear();
        }
    }

    public synchronized void setRecipeRegistry(final ClientboundUpdateRecipesPacket packet) {
        this.itemSets.clear();
        this.stoneCutterRecipes.clear();
        this.itemSets.putAll(packet.getItemSets());
        this.stoneCutterRecipes.addAll(packet.getStonecutterRecipes());
    }

    public void addRecipeBookEntries(final ClientboundRecipeBookAddPacket packet) {
        if (packet.isReplace()) {
            this.recipeBookEntries.clear();
        }
        for (var entry : packet.getEntries()) {
            int key = entry.contents().id();
            this.recipeBookEntries.put(key, entry.contents());
        }
    }

    public void setRecipeBookSettings(final ClientboundRecipeBookSettingsPacket packet) {
        this.recipeBookSettings.put("crafting", packet.getCrafting());
        this.recipeBookSettings.put("furnace", packet.getFurnace());
        this.recipeBookSettings.put("blastFurnace", packet.getBlastFurnace());
        this.recipeBookSettings.put("smoker", packet.getSmoker());
    }

    public void removeRecipeBookEntries(final ClientboundRecipeBookRemovePacket packet) {
        for (var id : packet.getRecipes()) {
            this.recipeBookEntries.remove(id);
        }
    }

    @Override
    public String getSendingMessage()  {
        return String.format("Sending %d recipes", this.itemSets.size());
    }

}
