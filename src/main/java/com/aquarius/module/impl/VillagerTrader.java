package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.aquarius.Proxy;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.discord.Embed;
import com.aquarius.discord.EmbedSerializer;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.actions.*;
import com.aquarius.feature.inventory.util.InventoryActionMacros;
import com.aquarius.feature.inventory.util.InventoryUtil;
import com.aquarius.feature.pathfinder.Baritone;
import com.aquarius.feature.pathfinder.PathingRequestFuture;
import com.aquarius.mc.enchantment.EnchantmentRegistry;
import com.aquarius.mc.item.ContainerTypeInfoRegistry;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.PacketHandlerCodec;
import com.aquarius.network.codec.PacketHandlerStateCodec;
import com.aquarius.util.RequestFuture;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.util.config.Config.Client.Extra.VillagerTrader.Trade;
import com.aquarius.util.config.Config.Client.Extra.VillagerTrader.Trade.PostTradeStoreMode;
import com.aquarius.util.config.Config.Client.Extra.VillagerTrader.TradeGroup;
import com.aquarius.util.math.MathHelper;
import com.aquarius.util.timer.Timer;
import com.aquarius.util.timer.Timers;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.VillagerData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.VillagerTrade;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

public class VillagerTrader extends Module {
    private State state = State.ENTRYPOINT;
    private final Cache<UUID, Boolean> interactedVillagersCache = CacheBuilder.newBuilder()
        .build();
    private final TradeIterator tradeIterator = new TradeIterator();
    private PathingRequestFuture restockPathingFuture = PathingRequestFuture.rejected;
    private RequestFuture restockWithdrawFuture = RequestFuture.rejected;
    private RequestFuture emeraldBlockCraftFuture = RequestFuture.rejected;
    private PathingRequestFuture interactWithVillagerFuture = PathingRequestFuture.rejected;
    private ClientboundMerchantOffersPacket offersPacket = null;
    private RequestFuture purchaseFuture = RequestFuture.rejected;
    private PathingRequestFuture storePathingFuture = PathingRequestFuture.rejected;
    private RequestFuture storeDepositFuture = RequestFuture.rejected;
    private PathingRequestFuture postTradePathingFuture = PathingRequestFuture.rejected;
    private RequestFuture postTradeDepositFuture = RequestFuture.rejected;
    private final Timer waitForRestockTimer = Timers.tickTimer();
    private final Timer waitForInteractTimer = Timers.tickTimer();
    private final Timer idleRecheckTimer = Timers.tickTimer();
    // Ids of trades currently out of supply (chest empty + can't afford). Cleared on a successful restock / re-probe.
    private final Set<String> exhaustedTrades = new HashSet<>();
    private int preTradeOutputCount = 0;
    private int preTradeInput1Count = 0;
    private int preTradeInput2Count = 0;
    private int outputBuyCount = 0;
    private int input1SellCount = 0;
    private int input2SellCount = 0;
    private long tradeStartTime = System.nanoTime();
    // --- villager offer memory (in-memory; rebuilt on (re)connect + trade-list change) ---
    // UUID -> set of configured trade ids this villager is known to offer. A villager that offers none of the
    // configured trades goes in uselessVillagers and is never opened again until the cache is cleared.
    private final Map<UUID, Set<String>> villagerTradeCapabilities = new HashMap<>();
    private final Set<UUID> uselessVillagers = new HashSet<>();
    private UUID currentVillagerUuid = null;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.villagerTrader.enabled;
    }

    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, e -> reset()),
            of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private int getPriority() {
        return Baritone.getPriority() + 100;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Trade groups: a member trade shares its group's INPUT supply profile (give chests / restock / carry caps /
    // overflow / post-trade mode) instead of its own. Output chest + offer stay per-trade. An emerald-EARNING member
    // (outputItem == emerald) is the exception — it keeps its own input chest (the sell-item source) and its emerald
    // output is kept, not stored, to refill the group.
    // ----------------------------------------------------------------------------------------------------------------

    /** True for an emerald-producing member (sells items FOR emeralds to refill its group). */
    private boolean isEarner(Trade trade) {
        return trade.getOutputItem() == ItemRegistry.EMERALD;
    }

    /** The enabled group a trade belongs to, or null if ungrouped / group missing / group disabled. */
    private @Nullable TradeGroup groupOf(Trade trade) {
        if (trade.group == null || trade.group.isEmpty()) return null;
        var g = CONFIG.client.extra.villagerTrader.groups.get(trade.group);
        return (g != null && g.enabled) ? g : null;
    }

    /** The group a trade draws its INPUT supply from, or null when it uses its own per-trade fields (ungrouped or earner). */
    private @Nullable TradeGroup supplyGroup(Trade trade) {
        if (isEarner(trade)) return null;
        return groupOf(trade);
    }

    private BlockPos give1Chest(Trade t)        { var g = supplyGroup(t); return g != null ? g.inputItem1Chest : t.inputItem1Chest; }
    private BlockPos give2Chest(Trade t)        { var g = supplyGroup(t); return g != null ? g.inputItem2Chest : t.inputItem2Chest; }
    private int give1RestockStacks(Trade t)     { var g = supplyGroup(t); return g != null ? g.inputItem1RestockStacks : t.inputItem1RestockStacks; }
    private int give1RestockThreshold(Trade t)  { var g = supplyGroup(t); return g != null ? g.inputItem1RestockCountThreshold : t.inputItem1RestockCountThreshold; }
    private int give2RestockStacks(Trade t)     { var g = supplyGroup(t); return g != null ? g.inputItem2RestockStacks : t.inputItem2RestockStacks; }
    private int give2RestockThreshold(Trade t)  { var g = supplyGroup(t); return g != null ? g.inputItem2RestockCountThreshold : t.inputItem2RestockCountThreshold; }
    private int give1MaxCarry(Trade t)          { var g = supplyGroup(t); return g != null ? g.inputItem1MaxCarryStacks : t.inputItem1MaxCarryStacks; }
    private int give2MaxCarry(Trade t)          { var g = supplyGroup(t); return g != null ? g.inputItem2MaxCarryStacks : t.inputItem2MaxCarryStacks; }
    private PostTradeStoreMode postTradeMode(Trade t) { var g = supplyGroup(t); return g != null ? g.postTradeStoreMode : t.postTradeStoreMode; }
    private BlockPos overflowChest(Trade t)     { var g = supplyGroup(t); return g != null ? g.overflowChestPos : t.overflowChestPos; }

    private int carriedEmeralds() {
        return countItem(ItemRegistry.EMERALD.id()) + 9 * countItem(ItemRegistry.EMERALD_BLOCK.id());
    }

    /** After a restock pass, can this trade actually do at least one purchase (does it hold each required input)? */
    private boolean tradeInputsAvailable(Trade trade) {
        if (countItem(trade.getInputItem1().id()) <= 0) return false;
        if (trade.has2InputTrade() && countItem(trade.getInputItem2().id()) <= 0) return false;
        return true;
    }

    /** A grouped spender that consumes emeralds and is at/below its group's emerald floor (or simply out of emeralds). */
    private boolean needsEmeraldRefill(Trade trade) {
        if (isEarner(trade) || !trade.hasEmeraldInputs()) return false;
        var g = groupOf(trade);
        if (g == null) return false; // self-refill only applies within a group
        int floor = Math.max(g.minEmeralds, 0); // 0 = passive: only when emeralds are actually gone
        return carriedEmeralds() <= floor;
    }

    /** The first enabled, non-exhausted earner sharing {@code spender}'s group, or null. */
    private @Nullable Trade pickGroupEarner(Trade spender) {
        if (spender.group == null || spender.group.isEmpty()) return null;
        for (var e : CONFIG.client.extra.villagerTrader.trades.entrySet()) {
            var t = e.getValue();
            if (!t.enabled || !isEarner(t)) continue;
            if (!spender.group.equals(t.group)) continue;
            if (exhaustedTrades.contains(e.getKey())) continue;
            return t;
        }
        return null;
    }

    private boolean allEnabledTradesExhausted() {
        boolean any = false;
        for (var e : CONFIG.client.extra.villagerTrader.trades.entrySet()) {
            if (!e.getValue().enabled) continue;
            any = true;
            if (!exhaustedTrades.contains(e.getKey())) return false;
        }
        return any;
    }

    /**
     * Reached the trading phase but the trade can't be funded (its supply chest is empty). Mark it exhausted, then:
     * try to run a group earner to refill emeralds; else, if EVERY enabled trade is exhausted, park (idle in place)
     * instead of wandering; else advance to the next trade.
     */
    private void onTradeOutOfSupply(Trade trade) {
        var id = getTradeId(trade);
        if (id != null) exhaustedTrades.add(id);
        if (needsEmeraldRefill(trade)) {
            var earner = pickGroupEarner(trade);
            if (earner != null && earner != trade) {
                info("Group '{}' out of emeralds — running earner '{}' to refill", trade.group, getTradeId(earner));
                tradeIterator.pointAt(earner);
                setState(State.ENTRYPOINT);
                return;
            }
        }
        if (allEnabledTradesExhausted()) {
            warn("All trades out of supply — parking until the supply chests are refilled");
            BARITONE.stop();
            idleRecheckTimer.reset();
            setState(State.IDLE_PARKED);
            return;
        }
        setState(State.READY_NEXT_TRADE);
    }

    private void reset() {
        state = State.ENTRYPOINT;
        interactedVillagersCache.invalidateAll();
        offersPacket = null;
        currentVillagerUuid = null;
        clearVillagerCache();
        waitForInteractTimer.reset();
        waitForRestockTimer.reset();
        idleRecheckTimer.reset();
        exhaustedTrades.clear();
        tradeIterator.reset();
        resetTradeCounter();
    }

    /** Forget everything learned about which villagers offer which trades (forces a fresh learning sweep). */
    public void clearVillagerCache() {
        villagerTradeCapabilities.clear();
        uselessVillagers.clear();
    }

    public String villagerCacheSummary() {
        return "known villagers: " + villagerTradeCapabilities.size()
            + ", skipped (offer nothing wanted): " + uselessVillagers.size();
    }

    private void resetTradeCounter() {
        preTradeOutputCount = 0;
        preTradeInput1Count = 0;
        preTradeInput2Count = 0;;
        outputBuyCount = 0;
        input1SellCount = 0;
        input2SellCount = 0;
        tradeStartTime = System.nanoTime();
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("villager-trader")
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .inbound(ClientboundMerchantOffersPacket.class, this::onMerchantOffers)
                .build())
            .build();
    }

    private ClientboundMerchantOffersPacket onMerchantOffers(ClientboundMerchantOffersPacket packet, ClientSession session) {
        this.offersPacket = packet;
        debug("Offers: {}", packet);
        return packet;
    }

    private void onTick(ClientBotTick event) {
        if (Proxy.getInstance().isInQueue() || !CACHE.getPlayerCache().getThePlayer().isAlive()) {
            state = State.ENTRYPOINT;
            return;
        }
        switch (state) {
            case ENTRYPOINT -> {
                if (!tradeIterator.hasNext())
                    return;
                resetTradeCounter();
                interactedVillagersCache.invalidateAll();
                setState(State.EVAL_RESTOCK);
            }
            case EVAL_RESTOCK -> {
                var trade = tradeIterator.current();
                // Proactive self-refill: a grouped spender at/below its emerald floor detours to a group earner to top
                // up before spending (with minEmeralds=0 this only fires once emeralds are actually gone).
                if (needsEmeraldRefill(trade)) {
                    var earner = pickGroupEarner(trade);
                    if (earner != null && earner != trade) {
                        debug("Group '{}' below emerald floor — running earner '{}' first", trade.group, getTradeId(earner));
                        tradeIterator.pointAt(earner);
                        setState(State.ENTRYPOINT);
                        return;
                    }
                }
                // Hard carry caps: shed any input held above the configured ceiling before (re)stocking, so the
                // bot never hoards stacks of a shared input (e.g. books) it pulled but never sold.
                if (overCarryCap(trade.getInputItem2(), give2MaxCarry(trade))) {
                    setState(State.DEPOSIT_EXCESS_INPUT_2_GO_TO_CHEST);
                    return;
                }
                if (overCarryCap(trade.getInputItem1(), give1MaxCarry(trade))) {
                    setState(State.DEPOSIT_EXCESS_INPUT_1_GO_TO_CHEST);
                    return;
                }
                var input1 = ItemRegistry.REGISTRY.get(trade.inputItem1);
                int input1Count = countItem(input1.id());
                if (give1RestockThreshold(trade) > input1Count) {
                    setState(State.RESTOCK_INPUT_1_GO_TO_CHEST);
                    return;
                }
                if (trade.has2InputTrade()) {
                    var input2 = ItemRegistry.REGISTRY.get(trade.inputItem2);
                    int input2Count = countItem(input2.id());
                    if (give2RestockThreshold(trade) > input2Count) {
                        setState(State.RESTOCK_INPUT_2_GO_TO_CHEST);
                        return;
                    }
                }
                // Earners keep their emerald output (it funds the group) — never store it.
                if (!isEarner(trade)) {
                    var output = ItemRegistry.REGISTRY.get(trade.outputItem);
                    int outputCount = countItem(output.id());
                    if (outputCount > trade.outputItemStoreCountThreshold) {
                        setState(State.STORE_GO_TO_CHEST);
                        return;
                    }
                }
                setState(State.CRAFT_EMERALD_BLOCKS);
            }
            case RESTOCK_INPUT_1_GO_TO_CHEST -> {
                var trade = tradeIterator.current();
                var chest = give1Chest(trade);
                restockPathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                restockPathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.RESTOCK_INPUT_1_WITHDRAW);
            }
            case RESTOCK_INPUT_1_WITHDRAW -> {
                if (restockPathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var input1 = ItemRegistry.REGISTRY.get(trade.inputItem1);
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        var actions = Lists.newArrayList(
                            InventoryActionMacros.withdraw(
                                openContainer.getContainerId(),
                                i -> {
                                    if (input1 == ItemRegistry.EMERALD) {
                                        return i.getId() == input1.id() || i.getId() == ItemRegistry.EMERALD_BLOCK.id();
                                    }
                                    return i.getId() == input1.id();
                                },
                                give1RestockStacks(trade)));
                        actions.add(new CloseContainer(openContainer.getContainerId()));
                        var request = InventoryActionRequest.builder()
                            .owner(this)
                            .actions(actions)
                            .priority(getPriority())
                            .build();
                        restockWithdrawFuture = INVENTORY.submit(request);
                        setState(State.RESTOCK_INPUT_1_AWAIT_WITHDRAW);
                    } else {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            error("Timed out waiting for input 1 container to open");
                            setState(State.RESTOCK_INPUT_1_AWAIT_WITHDRAW);
                        }
                    }
                }
            }
            case RESTOCK_INPUT_1_AWAIT_WITHDRAW -> {
                if (restockWithdrawFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var input1 = ItemRegistry.REGISTRY.get(trade.inputItem1);
                    int input1Count = countItem(input1.id());
                    if (give1RestockThreshold(trade) > input1Count) {
                        error("Failed restocking sufficient {} for trade: {}", input1.name(), trade.outputItem);
                    }
                    if (trade.has2InputTrade()) {
                        setState(State.RESTOCK_INPUT_2_GO_TO_CHEST);
                    } else {
                        setState(State.CRAFT_EMERALD_BLOCKS);
                    }
                }
            }
            case RESTOCK_INPUT_2_GO_TO_CHEST -> {
                var trade = tradeIterator.current();
                var chest = give2Chest(trade);
                restockPathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                restockPathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.RESTOCK_INPUT_2_WITHDRAW);
            }
            case RESTOCK_INPUT_2_WITHDRAW -> {
                if (restockPathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var input2 = ItemRegistry.REGISTRY.get(trade.inputItem2);
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        var actions = Lists.newArrayList(
                            InventoryActionMacros.withdraw(
                                openContainer.getContainerId(),
                                i -> {
                                    if (input2 == ItemRegistry.EMERALD) {
                                        return i.getId() == input2.id() || i.getId() == ItemRegistry.EMERALD_BLOCK.id();
                                    }
                                    return i.getId() == input2.id();
                                },
                                give2RestockStacks(trade)));
                        actions.add(new CloseContainer(openContainer.getContainerId()));
                        var request = InventoryActionRequest.builder()
                            .owner(this)
                            .actions(actions)
                            .priority(getPriority())
                            .build();
                        restockWithdrawFuture = INVENTORY.submit(request);
                        setState(State.RESTOCK_INPUT_2_AWAIT_WITHDRAW);
                    } else {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            error("Timed out waiting for input 2 container to open");
                            setState(State.RESTOCK_INPUT_2_AWAIT_WITHDRAW);
                        }
                    }
                }
            }
            case RESTOCK_INPUT_2_AWAIT_WITHDRAW -> {
                if (restockWithdrawFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var input2 = ItemRegistry.REGISTRY.get(trade.inputItem2);
                    int input2Count = countItem(input2.id());
                    if (give2RestockThreshold(trade) > input2Count) {
                        error("Failed restocking sufficient {} for trade: {}", input2.name(), trade.outputItem);
                    }
                    setState(State.CRAFT_EMERALD_BLOCKS);
                }
            }
            case DEPOSIT_EXCESS_INPUT_1_GO_TO_CHEST -> {
                var trade = tradeIterator.current();
                var chest = give1Chest(trade);
                storePathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.DEPOSIT_EXCESS_INPUT_1_DEPOSIT);
            }
            case DEPOSIT_EXCESS_INPUT_1_DEPOSIT -> {
                if (storePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.DEPOSIT_EXCESS_INPUT_1_GO_TO_CHEST);
                        }
                        return;
                    }
                    var input1 = trade.getInputItem1();
                    int slotsToDeposit = Math.max(0, countSlotUsages(input1.id()) - give1MaxCarry(trade));
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> input1.id() == i.getId(),
                            slotsToDeposit));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    storeDepositFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build());
                    setState(State.DEPOSIT_EXCESS_INPUT_1_AWAIT_DEPOSIT);
                }
            }
            case DEPOSIT_EXCESS_INPUT_1_AWAIT_DEPOSIT -> {
                if (storeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    info("Deposited excess {} back to chest (cap {} stacks)", trade.inputItem1, give1MaxCarry(trade));
                    setState(State.EVAL_RESTOCK);
                }
            }
            case DEPOSIT_EXCESS_INPUT_2_GO_TO_CHEST -> {
                var trade = tradeIterator.current();
                var chest = give2Chest(trade);
                storePathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.DEPOSIT_EXCESS_INPUT_2_DEPOSIT);
            }
            case DEPOSIT_EXCESS_INPUT_2_DEPOSIT -> {
                if (storePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.DEPOSIT_EXCESS_INPUT_2_GO_TO_CHEST);
                        }
                        return;
                    }
                    var input2 = trade.getInputItem2();
                    int slotsToDeposit = Math.max(0, countSlotUsages(input2.id()) - give2MaxCarry(trade));
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> input2.id() == i.getId(),
                            slotsToDeposit));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    storeDepositFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build());
                    setState(State.DEPOSIT_EXCESS_INPUT_2_AWAIT_DEPOSIT);
                }
            }
            case DEPOSIT_EXCESS_INPUT_2_AWAIT_DEPOSIT -> {
                if (storeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    info("Deposited excess {} back to chest (cap {} stacks)", trade.inputItem2, give2MaxCarry(trade));
                    setState(State.EVAL_RESTOCK);
                }
            }
            case CRAFT_EMERALD_BLOCKS -> {
                int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockCount == 0) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    return;
                }
                if (countInvEmptySlots() < 4) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    return;
                }
                int emeraldBlockSlot = InventoryUtil.searchPlayerInventory(i -> i.getId() == ItemRegistry.EMERALD_BLOCK.id());
                if (emeraldBlockSlot == -1) {
                    setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    return;
                }
                List<InventoryAction> actions = Lists.newArrayList();
                actions.add(new PlaceRecipe(0, "minecraft:emerald", true));
                actions.add(new ShiftClick(0, ShiftClickItemAction.LEFT_CLICK));
                actions.add(new CloseContainer(0));
                emeraldBlockCraftFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(getPriority())
                    .build());
                setState(State.AWAIT_CRAFT_EMERALD_BLOCKS);
            }
            case AWAIT_CRAFT_EMERALD_BLOCKS -> {
                if (emeraldBlockCraftFuture.isCompleted()) {
                    int emeraldBlockCount = countItem(ItemRegistry.EMERALD_BLOCK.id());
                    if (emeraldBlockCount > 0) {
                        setState(State.CRAFT_EMERALD_BLOCKS);
                    } else {
                        setState(State.TRADING_INTERACT_WITH_VILLAGER);
                    }
                }
            }
            case TRADING_INTERACT_WITH_VILLAGER -> {
                var trade = tradeIterator.current();
                // Restock has already run; if we still can't fund a single purchase, the supply chest is empty.
                // Don't sweep the whole hall buying nothing — refill from a group earner, or park, or move on.
                if (!tradeInputsAvailable(trade)) {
                    onTradeOutOfSupply(trade);
                    return;
                }
                var id = getTradeId(trade);
                if (id != null) exhaustedTrades.remove(id); // supply is back for this trade
                var nextVillagerOptional = nextVillager(trade);
                if (nextVillagerOptional.isEmpty()) {
                    if (!interactedVillagersCache.asMap().isEmpty()) {
                        if (countItem(trade.getOutputItem().id()) > 0) {
                            setState(State.STORE_GO_TO_CHEST);
                        } else {
                            setState(State.READY_NEXT_TRADE);
                        }
                    } else if (CONFIG.client.extra.villagerTrader.targetKnownVillagers && anyMatchingProfessionVillager(trade)) {
                        // Targeting is on and matching-profession villagers ARE present, but none is known to offer
                        // this trade (the rest are useless or known to offer only other trades) — don't hot-loop
                        // the restock check, just move on to the next trade.
                        debug("No villager offers trade {}; advancing", getTradeId(trade));
                        setState(State.READY_NEXT_TRADE);
                    } else {
                        warn("No villagers found to trade with, going back to restock chest");
                        setState(State.EVAL_RESTOCK);
                    }
                    return;
                }
                var nextVillager = nextVillagerOptional.get();
                currentVillagerUuid = nextVillager.getUuid();
                offersPacket = null;
                interactWithVillagerFuture = BARITONE.rightClickEntity(nextVillager);
                interactWithVillagerFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                interactedVillagersCache.put(nextVillager.getUuid(), true);
                setState(State.TRADING_AWAIT_INTERACT_WITH_VILLAGER);
            }
            case TRADING_AWAIT_INTERACT_WITH_VILLAGER -> {
                if (interactWithVillagerFuture.isCompleted()) {
                    if (offersPacket == null) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        }
                        return;
                    }
                    if (offersPacket.getContainerId() != CACHE.getPlayerCache().getInventoryCache().getOpenContainerId()) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.TRADING_INTERACT_WITH_VILLAGER);
                        }
                        return;
                    }
                    setState(State.TRADING_TRY_START_PURCHASE);
                }
            }
            case TRADING_TRY_START_PURCHASE -> {
                var trade = tradeIterator.current();
                recordVillagerCapabilities(currentVillagerUuid, offersPacket);
                var trades = offersPacket.getTrades();
                List<InventoryAction> actions = Lists.newArrayList();
                for (int i = 0; i < trades.length; i++) {
                    var villagerTrade = trades[i];
                    if (villagerTrade.isTradeDisabled()) continue;
                    if (!villagerOfferMatchesTrade(villagerTrade, trade)) continue;

                    int availableTradeCount = villagerTrade.getMaxUses() - villagerTrade.getNumUses(); // each shift click can consume many trades

                    int input1StackSize = ItemRegistry.REGISTRY.get(trade.inputItem1).stackSize();

                    int baseCostInput1 = villagerTrade.getFirstInput().getAmount();
                    int input1DemandCost = Math.max(0, MathHelper.floorI((villagerTrade.getFirstInput().getAmount() * villagerTrade.getDemand() * villagerTrade.getPriceMultiplier())));
                    int input1Cost = MathHelper.clamp(baseCostInput1 + input1DemandCost + villagerTrade.getSpecialPrice(), 1, input1StackSize);
                    if (input1Cost > trade.maxInput1PerTrade) continue;
                    int maxTradesPerInputStack = input1StackSize / input1Cost;
                    int input1Count = countItem(trade.getInputItem1().id());
                    int maxTradesForInput1 = input1Count / input1Cost;
                    int maxTradeCount = Math.min(availableTradeCount, maxTradesForInput1);

                    if (maxTradeCount == 0) {
                        info("Can't trade because not enough {} (have {}, need at least {})", ItemRegistry.REGISTRY.get(trade.inputItem1).name(), input1Count, input1Cost);
                    }

                    if (trade.has2InputTrade()) {
                        int input2StackSize = trade.has2InputTrade() ? 64 : ItemRegistry.REGISTRY.get(trade.inputItem2).stackSize();
                        int baseCostInput2 = villagerTrade.getSecondInput().getAmount();
                        int input2DemandCost = Math.max(0, MathHelper.floorI((villagerTrade.getSecondInput().getAmount() * villagerTrade.getDemand() * villagerTrade.getPriceMultiplier())));
                        int input2Cost = MathHelper.clamp(baseCostInput2 + input2DemandCost + villagerTrade.getSpecialPrice(), 1, input2StackSize);
                        if (input2Cost > trade.maxInput2PerTrade) continue;
                        int tradersPerInput2Stack = villagerTrade.getSecondInput().getAmount() / input2Cost;
                        maxTradesPerInputStack = Math.min(maxTradesPerInputStack, tradersPerInput2Stack);
                        int input2Count = countItem(trade.getInputItem2().id());
                        int maxTradesForInput2 = input2Count / input2Cost;
                        boolean hadEnoughInput1ToTrade = maxTradeCount > 0;
                        maxTradeCount = Math.min(maxTradeCount, maxTradesForInput2);
                        if (maxTradeCount == 0 && hadEnoughInput1ToTrade) {
                            info("Can't trade because not enough {} (have {}, need {})", ItemRegistry.REGISTRY.get(trade.inputItem2).name(), input2Count, input2Cost);
                        }
                    }

                    if (canShiftClickPurchase(villagerTrade)) {
                        int outputsStackSize = ItemRegistry.REGISTRY.get(villagerTrade.getOutput().getId()).stackSize();
                        int maxTradesPerOutputStack = outputsStackSize / villagerTrade.getOutput().getAmount();
                        int maxTradesPerShiftClick = Math.min(maxTradesPerInputStack, maxTradesPerOutputStack);
                        int tradeCount = 0;
                        for (int j = 0; j < maxTradeCount; j+= maxTradesPerShiftClick) {
                            tradeCount++;
                            actions.add(new SelectTrade(offersPacket.getContainerId(), i));
                            actions.add(new ShiftClick(offersPacket.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
                        }
                        debug("shift clicking {} times, trade count: {} trades per shift click: {}", tradeCount, maxTradeCount, maxTradesPerShiftClick);
                    } else {
                        /**
                         * If there are multiple enchanted books available to trade
                         * and one's price is lower than our current
                         * a shift click will purchase the other books
                         *
                         * so we have to do left clicks only to buy one at a time, moving it to empty slots
                         *
                         * we could also close the inventory to have an empty slot found automatically
                         * but it doesn't play well with current logic for interacted villager and trade completion tracking
                         */
                        var emptySlots = findEmptySlots();
                        if (emptySlots.isEmpty()) {
                            info("Can't trade because we don't have any empty inventory slots");
                        }
                        maxTradeCount = Math.min(maxTradeCount, emptySlots.size());
                        for (int j = 0; j < maxTradeCount; j++) {
                            int outputSlot = emptySlots.removeFirst();
                            actions.add(new SelectTrade(offersPacket.getContainerId(), i));
                            actions.add(new ClickItem(offersPacket.getContainerId(), 2, ClickItemAction.LEFT_CLICK));
                            actions.add(new ClickItem(offersPacket.getContainerId(), outputSlot, ClickItemAction.LEFT_CLICK));
                        }
                        debug("click trading {} times", maxTradeCount);
                    }
                }
                actions.add(new CloseContainer(offersPacket.getContainerId()));
                purchaseFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .priority(getPriority())
                    .actions(actions)
                    .build());
                preTradeInput1Count = countItem(trade.getInputItem1().id());
                preTradeInput2Count = trade.has2InputTrade() ? countItem(trade.getInputItem2().id()) : 0;
                preTradeOutputCount = countItem(trade.getOutputItem().id());
                setState(State.TRADING_AWAIT_PURCHASE);
            }
            case TRADING_AWAIT_PURCHASE -> {
                if (purchaseFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var input1Sold = preTradeInput1Count - countItem(trade.getInputItem1().id());
                    info("Sold {} {}", input1Sold, trade.inputItem1);
                    input1SellCount += input1Sold;
                    if (trade.has2InputTrade()) {
                        var input2Sold = preTradeInput2Count - countItem(trade.getInputItem2().id());;
                        info("Sold {} {}", input2Sold, trade.inputItem2);
                        input2SellCount += input2Sold;
                    }
                    var outputBought = countItem(trade.getOutputItem().id()) - preTradeOutputCount;
                    info("Bought {} {}", outputBought, trade.getOutputItem());
                    outputBuyCount += outputBought;
                    if (!isEarner(trade) && countItem(trade.getOutputItem().id()) > trade.outputItemStoreCountThreshold) {
                        setState(State.STORE_GO_TO_CHEST);
                    } else {
                        setState(State.EVAL_RESTOCK);
                    }
                }
            }
            case STORE_GO_TO_CHEST -> {
                var trade = tradeIterator.current();
                var storeChest = trade.outputChest;
                storePathingFuture = BARITONE.rightClickBlock(storeChest.x(), storeChest.y(), storeChest.z());
                storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.STORE_DEPOSIT);
            }
            case STORE_DEPOSIT -> {
                if (storePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.STORE_GO_TO_CHEST);
                        }
                        return;
                    }
                    var outputItem = trade.getOutputItem();
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> outputItem.id() == i.getId()
                        ));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    storeDepositFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build());
                    storePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                    setState(State.STORE_AWAIT_DEPOSIT);
                }
            }
            case STORE_AWAIT_DEPOSIT -> {
                if (storeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    int buyItemCount = countItem(trade.getOutputItem().id());
                    if (buyItemCount > 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            warn("Unable to fully deposit buy items, trying to continue anyway");
                            setState(State.EVAL_RESTOCK);
                        }
                        return;
                    }
                    setState(State.EVAL_RESTOCK);
                }
            }
            case READY_NEXT_TRADE -> {
                var trade = tradeIterator.current();
                switch (postTradeMode(trade)) {
                    case NONE -> {
                        setState(State.NEXT_TRADE);
                    }
                    case TO_RESTOCK -> {
                        setState(State.POST_TRADE_INPUT_1_GO_TO);
                    }
                    case TO_OVERFLOW -> {
                        setState(State.POST_TRADE_OVERFLOW_GO_TO);
                    }
                }
            }
            case POST_TRADE_INPUT_1_GO_TO -> {
                var trade = tradeIterator.current();
                var chest = give1Chest(trade);
                postTradePathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.POST_TRADE_INPUT_1_DEPOSIT);
            }
            case POST_TRADE_INPUT_1_DEPOSIT -> {
                if (postTradePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var inputItem1 = trade.getInputItem1();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.POST_TRADE_INPUT_1_GO_TO);
                        }
                        return;
                    }
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> inputItem1.id() == i.getId()
                        ));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    var request = InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build();
                    postTradeDepositFuture = INVENTORY.submit(request);
                    postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                    setState(State.POST_TRADE_INPUT_1_AWAIT_DEPOSIT);
                }
            }
            case POST_TRADE_INPUT_1_AWAIT_DEPOSIT -> {
                if (postTradeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var inputItem1 = trade.getInputItem1();
                    if (countItem(inputItem1.id()) > 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            warn("Unable to fully deposit post trade input item 1, trying to continue anyway");
                        } else {
                            return;
                        }
                    }
                    if (trade.has2InputTrade()) {
                        setState(State.POST_TRADE_INPUT_2_GO_TO);
                    } else {
                        setState(State.NEXT_TRADE);
                    }
                }
            }
            case POST_TRADE_INPUT_2_GO_TO -> {
                var trade = tradeIterator.current();
                var chest = give2Chest(trade);
                postTradePathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.POST_TRADE_INPUT_2_DEPOSIT);
            }
            case POST_TRADE_INPUT_2_DEPOSIT -> {
                if (postTradePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var inputItem2 = trade.getInputItem2();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.POST_TRADE_INPUT_2_GO_TO);
                        }
                        return;
                    }
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> inputItem2.id() == i.getId()
                        ));
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    var request = InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build();
                    postTradeDepositFuture = INVENTORY.submit(request);
                    postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                    setState(State.POST_TRADE_INPUT_2_AWAIT_DEPOSIT);
                }
            }
            case POST_TRADE_INPUT_2_AWAIT_DEPOSIT -> {
                if (postTradeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var inputItem2 = trade.getInputItem2();
                    if (countItem(inputItem2.id()) > 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            warn("Unable to fully deposit post trade input item 2, trying to continue anyway");
                        } else {
                            return;
                        }
                    }
                    setState(State.NEXT_TRADE);
                }
            }
            case POST_TRADE_OVERFLOW_GO_TO -> {
                var trade = tradeIterator.current();
                var chest = overflowChest(trade);
                postTradePathingFuture = BARITONE.rightClickBlock(chest.x(), chest.y(), chest.z());
                postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                setState(State.POST_TRADE_OVERFLOW_DEPOSIT);
            }
            case POST_TRADE_OVERFLOW_DEPOSIT -> {
                if (postTradePathingFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() == 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            setState(State.POST_TRADE_OVERFLOW_GO_TO);
                        }
                        return;
                    }
                    var inputItem1 = trade.getInputItem1();
                    var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                            openContainer.getContainerId(),
                            i -> inputItem1.id() == i.getId()
                        ));
                    if (trade.has2InputTrade()) {
                        var inputItem2 = trade.getInputItem2();
                        actions.addAll(
                            InventoryActionMacros.deposit(
                                openContainer.getContainerId(),
                                i -> inputItem2.id() == i.getId()
                            ));
                    }
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    var request = InventoryActionRequest.builder()
                        .owner(this)
                        .priority(getPriority())
                        .actions(actions)
                        .build();
                    postTradeDepositFuture = INVENTORY.submit(request);
                    postTradePathingFuture.addExecutedListener(f -> waitForInteractTimer.reset());
                    setState(State.POST_TRADE_OVERFLOW_AWAIT_DEPOSIT);
                }
            }
            case POST_TRADE_OVERFLOW_AWAIT_DEPOSIT -> {
                if (postTradeDepositFuture.isCompleted()) {
                    var trade = tradeIterator.current();
                    var inputItem1 = trade.getInputItem2();
                    if (countItem(inputItem1.id()) > 0) {
                        if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                            warn("Unable to fully deposit post trade input item 1, trying to continue anyway");
                        } else {
                            return;
                        }
                    }
                    if (trade.has2InputTrade()) {
                        var inputItem2 = trade.getInputItem2();
                        if (countItem(inputItem2.id()) > 0) {
                            if (waitForInteractTimer.tick(CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks)) {
                                warn("Unable to fully deposit post trade input item 2, trying to continue anyway");
                            } else {
                                return;
                            }
                        }
                    }
                    setState(State.NEXT_TRADE);
                }
            }
            case NEXT_TRADE -> {
                var trade = tradeIterator.current();
                var nextTrade = tradeIterator.next();
                var tradeDuration = Duration.ofNanos(System.nanoTime() - tradeStartTime);
                var tradeResult = Embed.builder()
                    .title("Trade Completed")
                    .addField("Trade ID", Objects.requireNonNullElse(getTradeId(trade), "?"))
                    .addField("Duration", MathHelper.formatDuration(tradeDuration))
                    .addField("Input 1", trade.inputItem1)
                    .addField("Input 1 Sell Count", input1SellCount);
                if (trade.has2InputTrade()) {
                    tradeResult
                        .addField("Input 2", trade.inputItem2)
                        .addField("Input 2 Sell Count", input2SellCount);
                }
                tradeResult
                    .addField("Output", trade.outputItem)
                    .addField("Output Buy Count", outputBuyCount)
                    .addField("Next Trade", Objects.requireNonNullElse(getTradeId(nextTrade), "?"));
                if (CONFIG.client.extra.villagerTrader.logTradeStatusToDiscord) {
                    discordNotification(tradeResult);
                } else {
                    info(EmbedSerializer.serialize(tradeResult));
                }
                setState(State.ENTRYPOINT);
            }
            case IDLE_PARKED -> {
                // Every enabled trade is out of supply. Idle in place (no pathing churn) and periodically re-probe;
                // clearing the exhausted marks forces a real restock attempt rather than instantly re-parking.
                if (idleRecheckTimer.tick(CONFIG.client.extra.villagerTrader.idleRecheckTicks)) {
                    debug("Re-probing supply chests after park");
                    exhaustedTrades.clear();
                    setState(State.EVAL_RESTOCK);
                }
            }
        }
    }

    private boolean enchantmentFilter(Trade trade, @NonNull ItemStack output) {
        var dataComponents = output.getDataComponents();
        if (dataComponents == null)
            return false;
        var storedEnchantments = dataComponents.get(DataComponentTypes.STORED_ENCHANTMENTS);
        var enchantments = dataComponents.get(DataComponentTypes.ENCHANTMENTS);
        if (storedEnchantments == null && enchantments == null)
            return false;
        var itemEnchants = (storedEnchantments != null ? storedEnchantments : enchantments).getEnchantments();

        for (var bookEnchantEntry : trade.outputItemEnchantments.object2IntEntrySet()) {
            String desiredEnchantKey = bookEnchantEntry.getKey();
            var desiredEnchant = EnchantmentRegistry.REGISTRY.get(desiredEnchantKey);
            if (desiredEnchant == null) {
                error("Enchanted book enchantment id: {} not found in registry", desiredEnchantKey);
                continue;
            }
            var enchantId = desiredEnchant.id();
            int desiredLevel = bookEnchantEntry.getIntValue();
            if (!itemEnchants.containsKey(enchantId))
                continue;
            int actualLevel = itemEnchants.get(enchantId);
            if (actualLevel >= desiredLevel) {
                return true;
            }
        }
        return false;
    }

    private void stop() {
        CONFIG.client.extra.villagerTrader.enabled = false;
        syncEnabledFromConfig();
        saveConfigAsync();
    }

    private void setState(State newState) {
        debug("State change: {} -> {}", state, newState);
        this.state = newState;
    }

    private Optional<EntityLiving> nextVillager(final Trade trade) {
        var tradeId = getTradeId(trade);
        boolean targeting = CONFIG.client.extra.villagerTrader.targetKnownVillagers && tradeId != null;
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(e -> e.getEntityType() == EntityType.VILLAGER)
            .filter(e -> !interactedVillagersCache.asMap().containsKey(e.getUuid()))
            .map(e -> (EntityLiving) e)
            .filter(e -> trade.villagerProfession == getVillagerProfession(e))
            .filter(e -> !targeting || villagerIsCandidate(e.getUuid(), tradeId))
            .min(Comparator.comparingDouble(e -> e.distanceSqTo(CACHE.getPlayerCache().getThePlayer())));
    }

    /**
     * A villager is worth opening for {@code tradeId} if it is already KNOWN to offer that trade, or if it is
     * still UNKNOWN (never opened — open it once to learn what it offers). Villagers known to offer none of the
     * configured trades, or known to offer only other trades, are skipped.
     */
    private boolean villagerIsCandidate(UUID uuid, String tradeId) {
        if (uselessVillagers.contains(uuid)) return false;
        var caps = villagerTradeCapabilities.get(uuid);
        if (caps == null) return true; // unknown — learn it
        return caps.contains(tradeId);
    }

    private boolean anyMatchingProfessionVillager(final Trade trade) {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(e -> e.getEntityType() == EntityType.VILLAGER)
            .map(e -> (EntityLiving) e)
            .anyMatch(e -> trade.villagerProfession == getVillagerProfession(e));
    }

    /** Record, against every configured trade, which ones this villager's offers can fulfill. */
    private void recordVillagerCapabilities(UUID villagerUuid, ClientboundMerchantOffersPacket offers) {
        if (villagerUuid == null || offers == null) return;
        Set<String> caps = new HashSet<>();
        for (var entry : CONFIG.client.extra.villagerTrader.trades.entrySet()) {
            var t = entry.getValue();
            if (!t.enabled) continue;
            for (var villagerTrade : offers.getTrades()) {
                if (villagerOfferMatchesTrade(villagerTrade, t)) {
                    caps.add(entry.getKey());
                    break;
                }
            }
        }
        if (caps.isEmpty()) {
            uselessVillagers.add(villagerUuid);
            villagerTradeCapabilities.remove(villagerUuid);
        } else {
            uselessVillagers.remove(villagerUuid);
            villagerTradeCapabilities.put(villagerUuid, caps);
        }
    }

    /** Whether a villager's offer matches a configured trade by item + enchant (ignores price/affordability/sold-out). */
    private boolean villagerOfferMatchesTrade(VillagerTrade villagerTrade, Trade trade) {
        if (villagerTrade.getOutput() == null) return false;
        if (villagerTrade.getOutput().getId() != ItemRegistry.REGISTRY.get(trade.outputItem).id()) return false;
        if (villagerTrade.getFirstInput().getId() != ItemRegistry.REGISTRY.get(trade.inputItem1).id()) return false;
        if (trade.has2InputTrade()) {
            if (villagerTrade.getSecondInput() == null) return false;
            if (villagerTrade.getSecondInput().getId() != ItemRegistry.REGISTRY.get(trade.inputItem2).id()) return false;
        } else {
            if (villagerTrade.getSecondInput() != null) return false;
        }
        if (!trade.outputItemEnchantments.isEmpty()) {
            return enchantmentFilter(trade, villagerTrade.getOutput());
        }
        return true;
    }

    private boolean overCarryCap(com.aquarius.mc.item.ItemData item, int maxStacks) {
        if (maxStacks <= 0) return false;
        return countItem(item.id()) > maxStacks * item.stackSize();
    }

    private VillagerProfession getVillagerProfession(EntityLiving villager) {
        var data = villager.getMetadataValue(18, MetadataTypes.VILLAGER_DATA, VillagerData.class);
        if (data == null) {
            return VillagerProfession.NONE;
        }
        return VillagerProfession.from(data.getProfession());
    }

    private int countItem(int id) {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            if (item.getId() == id) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int countInvEmptySlots() {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            if (inv.get(i) == Container.EMPTY_STACK) {
                count++;
            }
        }
        return count;
    }

    private int countSlotUsages(int id) {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            if (item.getId() == id) {
                count++;
            }
        }
        return count;
    }

    private boolean canShiftClickPurchase(VillagerTrade villagerTrade) {
        int count = 0;
        for (var offer : offersPacket.getTrades()) {
            if (villagerTrade.getOutput().getId() != offer.getOutput().getId()) continue;
            if (villagerTrade.getFirstInput().getId() != offer.getFirstInput().getId()) continue;
            boolean offerHasInput2 = offer.getSecondInput() != Container.EMPTY_STACK;
            boolean villagerTradeHasInput2 = villagerTrade.getSecondInput() != Container.EMPTY_STACK;
            if (offerHasInput2 && villagerTradeHasInput2) {
                if (villagerTrade.getSecondInput().getId() == offer.getSecondInput().getId()) {
                    count++;
                }
            } else if (!offerHasInput2 && !villagerTradeHasInput2) {
                count++;
            }

        }
        if (count > 1) return false;
        return true;
    }

    private IntList findEmptySlots() {
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer.getType() != ContainerType.MERCHANT) return IntList.of();
        var output = new IntArrayList();
        var containerInfo = ContainerTypeInfoRegistry.REGISTRY.get(openContainer.getType());
        for (int i = containerInfo.topSlots(); i < containerInfo.totalSlots(); i++) {
            if (openContainer.getItemStack(i) == Container.EMPTY_STACK) {
                output.add(i);
            }
        }
        return output;
    }

    public void onTradeListChange() {
        reset();
    }

    @Nullable String getTradeId(Trade trade) {
        for (var entry : CONFIG.client.extra.villagerTrader.trades.entrySet()) {
            if (entry.getValue() == trade) {
                return entry.getKey();
            }
        }
        return null;
    }

    public enum State {
        ENTRYPOINT,
        EVAL_RESTOCK,
        RESTOCK_INPUT_1_GO_TO_CHEST,
        RESTOCK_INPUT_1_WITHDRAW,
        RESTOCK_INPUT_1_AWAIT_WITHDRAW,
        RESTOCK_INPUT_2_GO_TO_CHEST,
        RESTOCK_INPUT_2_WITHDRAW,
        RESTOCK_INPUT_2_AWAIT_WITHDRAW,
        DEPOSIT_EXCESS_INPUT_1_GO_TO_CHEST,
        DEPOSIT_EXCESS_INPUT_1_DEPOSIT,
        DEPOSIT_EXCESS_INPUT_1_AWAIT_DEPOSIT,
        DEPOSIT_EXCESS_INPUT_2_GO_TO_CHEST,
        DEPOSIT_EXCESS_INPUT_2_DEPOSIT,
        DEPOSIT_EXCESS_INPUT_2_AWAIT_DEPOSIT,
        CRAFT_EMERALD_BLOCKS,
        AWAIT_CRAFT_EMERALD_BLOCKS,
        TRADING_INTERACT_WITH_VILLAGER,
        TRADING_AWAIT_INTERACT_WITH_VILLAGER,
        TRADING_TRY_START_PURCHASE,
        TRADING_AWAIT_PURCHASE,
        // todo: states to compact stackable items in inventory with shift clicks
        STORE_GO_TO_CHEST,
        STORE_DEPOSIT,
        STORE_AWAIT_DEPOSIT,
        READY_NEXT_TRADE,
        POST_TRADE,
        POST_TRADE_INPUT_1_GO_TO,
        POST_TRADE_INPUT_1_DEPOSIT,
        POST_TRADE_INPUT_1_AWAIT_DEPOSIT,
        POST_TRADE_INPUT_2_GO_TO,
        POST_TRADE_INPUT_2_DEPOSIT,
        POST_TRADE_INPUT_2_AWAIT_DEPOSIT,
        POST_TRADE_OVERFLOW_GO_TO,
        POST_TRADE_OVERFLOW_DEPOSIT,
        POST_TRADE_OVERFLOW_AWAIT_DEPOSIT,
        NEXT_TRADE,
        AWAIT_RESTOCK,
        IDLE_PARKED
    }

    public enum VillagerProfession {
        NONE,
        ARMORER,
        BUTCHER,
        CARTOGRAPHER,
        CLERIC,
        FARMER,
        FISHERMAN,
        FLETCHER,
        LEATHERWORKER,
        LIBRARIAN,
        MASON,
        NITWIT,
        SHEPHERD,
        TOOLSMITH,
        WEAPONSMITH;

        private static final VillagerProfession[] VALUES = values();

        public static VillagerProfession from(int id) {
            return VALUES[id];
        }
    }

    public static class TradeIterator implements Iterator<Trade> {
        int index = 0;
        Trade[] backingArray = CONFIG.client.extra.villagerTrader.trades.values()
            .stream()
            .filter(trade -> trade.enabled)
            .toArray(Trade[]::new);

        @Override
        public boolean hasNext() {
            return backingArray.length > 0;
        }

        public Trade current() {
            return backingArray[index];
        }

        /** Point the iterator at a specific (enabled) trade so the next cycle runs it. No-op if it isn't in the set. */
        public boolean pointAt(Trade t) {
            for (int i = 0; i < backingArray.length; i++) {
                if (backingArray[i] == t) {
                    index = i;
                    return true;
                }
            }
            return false;
        }

        @Override
        public Trade next() {
            if (++index >= backingArray.length) {
                index = 0;
            }
            return backingArray[index];
        }

        public void refresh() {
            backingArray = CONFIG.client.extra.villagerTrader.trades.values()
                .stream()
                .filter(trade -> trade.enabled)
                .toArray(Trade[]::new);
            if (index >= backingArray.length) {
                index = 0;
            }
        }

        public void reset() {
            index = 0;
            refresh();
        }
    }
}
