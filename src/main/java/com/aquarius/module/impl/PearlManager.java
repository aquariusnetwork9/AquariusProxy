package com.aquarius.module.impl;

import com.aquarius.Proxy;
import com.aquarius.discord.Embed;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.mc.item.ItemData;
import com.aquarius.feature.inventory.actions.DropItem;
import com.aquarius.feature.inventory.actions.MoveToHotbarSlot;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.util.InventoryUtil;
import com.aquarius.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import com.aquarius.util.config.Config.Client.Extra.PearlPlus;
import com.aquarius.module.api.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static com.aquarius.Globals.*;

public class PearlManager {
    private final Module notifier;

    public PearlManager(Module notifier) {
        this.notifier = notifier;
    }

    public record PlayerPearl(UUID ownerUuid, String ownerName, PearlPlus.StoredPearl pearl) {
    }

    public Optional<PlayerPearl> findPearl(UUID ownerUuid, String pearlId) {
        if (ownerUuid == null || pearlId == null || pearlId.isBlank()) {
            return Optional.empty();
        }
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return Optional.empty();
        PearlPlus.StoredPearl stored = entry.pearls.get(pearlId);
        if (stored == null) return Optional.empty();
        return Optional.of(new PlayerPearl(ownerUuid, entry.playerName, stored));
    }

    public List<PearlPlus.StoredPearl> listPearls(UUID ownerUuid) {
        if (ownerUuid == null) return List.of();
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return List.of();
        return new ArrayList<>(entry.pearls.values());
    }

    public PearlPlus.StoredPearl recordPearl(UUID ownerUuid, String ownerName, String pearlId, int x, int y, int z) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.computeIfAbsent(ownerUuid, uuid -> new PearlPlus.PlayerPearls());
        entry.playerName = ownerName;
        if (entry.defaultPearlId == null || entry.defaultPearlId.isBlank()) {
            entry.defaultPearlId = pearlId;
        }
        PearlPlus.StoredPearl stored = entry.pearls.computeIfAbsent(pearlId, id -> new PearlPlus.StoredPearl());
        stored.pearlId = pearlId;
        stored.x = x;
        stored.y = y;
        stored.z = z;
        return stored;
    }

    public void removePearl(UUID ownerUuid, String pearlId) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return;
        entry.pearls.remove(pearlId);
        if (pearlId != null && pearlId.equals(entry.defaultPearlId)) {
            entry.defaultPearlId = entry.pearls.keySet().stream().findFirst().orElse(null);
        }
        if (entry.pearls.isEmpty()) {
            CONFIG.client.extra.pearlPlus.players.remove(ownerUuid);
        }
    }

    public boolean renamePearl(UUID ownerUuid, String oldPearlId, String newPearlId) {
        if (ownerUuid == null || oldPearlId == null || newPearlId == null
                || oldPearlId.isBlank() || newPearlId.isBlank()) {
            return false;
        }

        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return false;

        PearlPlus.StoredPearl existing = entry.pearls.get(oldPearlId);
        if (existing == null) return false;
        if (entry.pearls.containsKey(newPearlId)) return false;

        entry.pearls.remove(oldPearlId);
        existing.pearlId = newPearlId;
        entry.pearls.put(newPearlId, existing);

        if (oldPearlId.equals(entry.defaultPearlId)) {
            entry.defaultPearlId = newPearlId;
        }
        return true;
    }

    public String defaultPearlId(UUID ownerUuid) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return null;
        String configuredDefault = entry.defaultPearlId;
        if (configuredDefault != null && entry.pearls.containsKey(configuredDefault)) {
            if (!CONFIG.client.extra.pearlPlus.autoLoad.autoDefaultToPresent || isPearlPresent(entry.pearls.get(configuredDefault))) {
                return configuredDefault;
            }
        }

        if (CONFIG.client.extra.pearlPlus.autoLoad.autoDefaultToPresent) {
            for (Map.Entry<String, PearlPlus.StoredPearl> pearlEntry : entry.pearls.entrySet()) {
                if (isPearlPresent(pearlEntry.getValue())) {
                    return pearlEntry.getKey();
                }
            }
        }

        if (configuredDefault != null && entry.pearls.containsKey(configuredDefault)) {
            return configuredDefault;
        }

        return entry.pearls.keySet().stream().findFirst().orElse(null);
    }

    public void setDefaultPearl(UUID ownerUuid, String pearlId) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null) return;
        if (pearlId != null && entry.pearls.containsKey(pearlId)) {
            entry.defaultPearlId = pearlId;
        }
    }

    public String resolvePearlId(UUID ownerUuid, String pearlId) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null || pearlId == null) return null;
        return entry.pearls.keySet().stream()
                .filter(id -> id.equalsIgnoreCase(pearlId))
                .findFirst()
                .orElse(null);
    }

    public boolean isPearlPresent(PearlPlus.StoredPearl pearl) {
        if (pearl == null || CACHE == null || CACHE.getEntityCache() == null) {
            return false;
        }
        if (!isWithinPresenceRange(pearl)) {
            return false;
        }
        return CACHE.getEntityCache().getEntities().values().stream()
                .anyMatch(entity -> entity.getEntityType() == org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType.ENDER_PEARL
                        && Math.floor(entity.getX()) == pearl.x
                        && Math.floor(entity.getZ()) == pearl.z);
    }

    private boolean isWithinPresenceRange(PearlPlus.StoredPearl pearl) {
        if (CACHE == null || CACHE.getPlayerCache() == null || CACHE.getPlayerCache().getThePlayer() == null) {
            return false;
        }

        var player = CACHE.getPlayerCache().getThePlayer();
        double dx = player.getX() - pearl.x;
        double dy = player.getY() - pearl.y;
        double dz = player.getZ() - pearl.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance <= CONFIG.client.extra.pearlPlus.autoDetect.temporaryRemovalRange;
    }

    public void loadPearl(PearlPlus.StoredPearl pearl, String requesterName) {
        if (pearl == null) {
            return;
        }
        Proxy proxy = Proxy.getInstance();
        if (proxy == null || !proxy.isConnected() || proxy.isInQueue()) {
            notifier.discordAndIngameNotification(Embed.builder().title("Can't Load Pearl").description("Bot is not online").errorColor());
            return;
        }
        if (proxy.hasActivePlayer()) {
            notifier.discordAndIngameNotification(Embed.builder().title("Can't Load Pearl").description("Player is controlling").errorColor());
            return;
        }
        
        // Make sure there is a pearl to drop for the player before walking.
        if (CONFIG.client.extra.pearlPlus.autoLoad.dropPearlAfterLoad == true) {
            ensurePearlsAvailable();
        }

        BlockPos current = CACHE.getPlayerCache().getThePlayer().blockPos();
        BARITONE.rightClickBlock(pearl.x, pearl.y, pearl.z)
                .addExecutedListener(f -> {
                    var builder = Embed.builder()
                            .title("Pearl Loaded!")
                            .addField("Pearl ID", pearl.pearlId, false)
                            .successColor();
                    if (requesterName != null) {
                        builder.addField("Requested By", requesterName, false);
                    }
                    notifier.discordAndIngameNotification(builder);
                    
                     // Drop a pearl when loaded.
                    if (CONFIG.client.extra.pearlPlus.autoLoad.dropPearlAfterLoad == true) {
                        handlePearlDropAfterLoad(requesterName);
                    }

                    if (CONFIG.client.extra.pearlPlus.autoLoad.returnToStartPos) {
                        BARITONE.pathTo(current.x(), current.z())
                                .addExecutedListener(f2 -> notifier.discordAndIngameNotification(Embed.builder()
                                        .description("Returned to start pos")
                                        .successColor()));
                    }
                });
        notifier.discordAndIngameNotification(Embed.builder().title("Loading Pearl").addField("Pearl", pearl.pearlId, false).primaryColor());
    }

    public String pearlsList(UUID ownerUuid) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null || entry.pearls.isEmpty()) return "None";

        StringBuilder sb = new StringBuilder("PearlIDs: ");
        boolean first = true;
        for (Map.Entry<String, PearlPlus.StoredPearl> e : entry.pearls.entrySet()) {
            PearlPlus.StoredPearl pearl = e.getValue();
            if (!first) {
                sb.append(", ");
            }
            sb.append(pearl.pearlId);
            if (!isPearlPresent(pearl)) {
                sb.append("*");
            }
            first = false;
        }
        return sb.toString();
    }

    public String pearlsListWithCoords(UUID ownerUuid) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null || entry.pearls.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, PearlPlus.StoredPearl> e : entry.pearls.entrySet()) {
            PearlPlus.StoredPearl pearl = e.getValue();
            sb.append("**").append(pearl.pearlId).append("**");
            sb.append(": ");
            if (CONFIG.discord.reportCoords) {
                sb.append("||[").append(pearl.x).append(", ").append(pearl.y).append(", ").append(pearl.z).append("]||");
            } else {
                sb.append("coords hidden");
            }
            sb.append("\n");
        }
        String result = sb.toString();
        return result.isBlank() ? "None" : result.substring(0, result.length() - 1);
    }

    public String pearlsListWithCoordsAllPlayers() {
        if (CONFIG.client.extra.pearlPlus.players.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (var entry : CONFIG.client.extra.pearlPlus.players.entrySet()) {
            PearlPlus.PlayerPearls playerPearls = entry.getValue();
            if (playerPearls == null || playerPearls.pearls == null || playerPearls.pearls.isEmpty()) {
                continue;
            }
            String playerName = playerPearls.playerName != null ? playerPearls.playerName : entry.getKey().toString();
            sb.append("**").append(playerName).append("**").append("\n");
            for (PearlPlus.StoredPearl pearl : playerPearls.pearls.values()) {
                sb.append("- ").append(pearl.pearlId);
                sb.append(": ");
                if (CONFIG.discord.reportCoords) {
                    sb.append("||[").append(pearl.x).append(", ").append(pearl.y).append(", ").append(pearl.z).append("]||");
                } else {
                    sb.append("coords hidden");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        String result = sb.toString().trim();
        return result.isBlank() ? "None" : result;
    }

    public String nextAvailablePearlId(UUID ownerUuid, String ownerName) {
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        String configuredBase = CONFIG.client.extra.pearlPlus.defaultPearlId;
        String base;
        if (configuredBase != null && !configuredBase.isBlank()) {
            base = configuredBase;
        } else {
            base = (ownerName == null || ownerName.isBlank()) ? "pearl" : ownerName;
        }
        base = base.replaceAll("\\s+", "");
        if (entry == null || !entry.pearls.containsKey(base)) {
            return base;
        }

        int suffix = 2;
        while (suffix < 10_000) {
            String candidate = base + suffix;
            if (!entry.pearls.containsKey(candidate)) {
                return candidate;
            }
            suffix++;
        }
        return null;
    }

    public void info(String message) {
        MODULE_LOG.info(message);
    }

    // Check if first hotbar slot (slot 0) contains ender pearls
    private boolean hasPearlsInHotbarSlot0() {
        if (CACHE == null || CACHE.getPlayerCache() == null) {
            return false;
        }
        
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        if (playerInventory == null) {
            return false;
        }
        
        var itemStack = playerInventory.get(36); // Hotbar slot 0 = index 36
        if (itemStack == null) {
            return false;
        }
        
        return isEnderPearl(itemStack);
    }

    // Find pearls in inventory and move to hotbar slot 0
    private boolean ensurePearlsAvailable() {
        if (hasPearlsInHotbarSlot0()) {
            return true;
        }
        
        // Search for pearls in inventory
        int pearlSlot = findEnderPearlInInventory();
        if (pearlSlot == -1) {
            return false; // No pearls found
        }
        
        // Move pearls to hotbar slot 0
        try {
            INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(new MoveToHotbarSlot(pearlSlot, MoveToHotbarAction.SLOT_1))
                .priority(1000)
                .build());
                info("Moved pearls to hotbar");
            return true;
        } catch (Exception e) {
            MODULE_LOG.warn("Failed to move pearls to hotbar slot 0", e);
            return false;
        }
    }

    // Drop one pearl from hotbar slot 0
    private void dropPearlFromHotbarSlot0() {
        try {
            INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(new DropItem(36, org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction.DROP_FROM_SELECTED))
                .priority(1000)
                .build());
        } catch (Exception e) {
            MODULE_LOG.warn("Failed to drop pearl from hotbar slot 0", e);
        }
    }

    // Send out-of-pearls message to player
    private void sendOutOfPearlsMessage(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        
        String message = "I'm all out of pearls, can you give me some?";
        notifier.sendClientPacketAsync(ChatUtil.getWhisperChatPacket(playerName, message));
        info("Sent out-of-pearls message to " + playerName);
    }

    /**
     * Drops a new pearl to the player that just got teleported.
     * Will beg them for a restock if the bot ran out of pearls.
     * 
     * @param playerName    
     */
    public void handlePearlDropAfterLoad(String playerName) {
        if (!CONFIG.client.extra.pearlPlus.autoLoad.dropPearlAfterLoad) {
            return;
        }
        
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        
        info("Attempting to drop pearl for " + playerName);
        
        if (ensurePearlsAvailable()) {
            dropPearlFromHotbarSlot0();
            info("Successfully dropped pearl for " + playerName);
        } else {
            sendOutOfPearlsMessage(playerName);
            info("No pearls available to drop for " + playerName + ". Begged them to drop me some.");
        }
    }

    private boolean isEnderPearl(Object itemStack) {
        if (itemStack == null) {
            return false;
        }
        
        try {
            // Check if it's an ItemStack and get the item ID
            int itemId = -1;
            if (itemStack instanceof org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack) {
                itemId = ((org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack) itemStack).getId();
            } else if (itemStack instanceof com.aquarius.cache.data.inventory.Container) {
                // Handle Container.EMPTY_STACK case
                return false;
            }
            
            if (itemId == -1) {
                return false;
            }
            
            ItemData itemData = ItemRegistry.REGISTRY.get(itemId);
            if (itemData == null) {
                return false;
            }
            info(itemData.name());
            info("id: "+itemId);
            
            String itemName = itemData.name();
            
            return itemName != null && (
            "ENDER_PEARL".equals(itemName) ||
            "ender_pearl".equals(itemName) ||
            itemName.contains("ENDER_PEARL") ||
            itemName.contains("ender_pearl")
        );

        } catch (Exception e) {
            MODULE_LOG.debug("Error checking if item is ender pearl", e);
            return false;
        }
    }

    // Helper method to find ender pearls in inventory
    private int findEnderPearlInInventory() {
        if (CACHE == null || CACHE.getPlayerCache() == null) {
            return -1;
        }
        
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        if (playerInventory == null) {
            return -1;
        }
        
        // Search through all inventory slots (9-44, excluding hotbar 36-44)
        for (int i = 9; i < playerInventory.size(); i++) {
            var itemStack = playerInventory.get(i);
            if (itemStack != null && isEnderPearl(itemStack)) {
                return i;
            }
        }
        
        return -1;
    }

    /**
     * Returns the amount of pearls a player has left in the stasis. 
     * 
     * @param ownerUuid UUID of the player we want to check the pearlcount for. 
     * @return  Pearlcount
     */
    public int countPresentPearls(UUID ownerUuid) {
        if (ownerUuid == null) return 0;
        PearlPlus.PlayerPearls entry = CONFIG.client.extra.pearlPlus.players.get(ownerUuid);
        if (entry == null || entry.pearls == null) return 0;
        
        int presentCount = 0;
        for (PearlPlus.StoredPearl pearl : entry.pearls.values()) {
            if (isPearlPresent(pearl)) {
                presentCount++;
            }
        }
        
        return presentCount;
    }

    /**
     * Looks up the uuid from a playername. 
     * 
     * @param username
     * @return
     */
    public UUID getUuidFromUsername(String username) {
        if (username == null || username.isBlank()) return null;
        
        for (var entry : CONFIG.client.extra.pearlPlus.players.entrySet()) {
            UUID uuid = entry.getKey();
            PearlPlus.PlayerPearls playerPearls = entry.getValue();
            if (playerPearls != null && username.equals(playerPearls.playerName)) {
                return uuid;
            }
        }
        return null;
    }
}
