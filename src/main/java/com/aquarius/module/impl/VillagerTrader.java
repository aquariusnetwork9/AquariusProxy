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
    // The trade whose sweep just finished, driving the current AWAIT_RESTOCK wait (its group's cooldown if grouped,
    // else its own). Re-read live each tick so editing the cooldown mid-wait takes effect. Cleared on reset().
    private Trade cooldownTrade = null;
    // Per-(villager, live offer index) restock/demand memory for Trade#priceControlEnabled. Keyed by offer INDEX
    // (not trade id) since one configured trade can match multiple live offers on the same villager (e.g. two
    // enchant-level tiers of a book — see canShiftClickPurchase), each accruing Minecraft demand independently.
    // Deliberately NOT cleared in reset()/on reconnect (see reset()) — only ".trader price reset" clears it.
    private final Map<UUID, Map<Integer, OfferPriceState>> offerPriceStateByVillager = new HashMap<>();

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

    /**
     * Effective post-cycle cooldown (seconds) for a trade: inherited from its group when grouped (all members,
     * earners included, share the group's pacing), else the trade's own value. Clamped to [0, 1200] on read so a
     * hand-edited / API-set out-of-range value can't misbehave.
     */
    private int effectiveCooldownSeconds(Trade trade) {
        var g = groupOf(trade);
        int secs = (g != null) ? g.cycleCooldownSeconds : trade.cycleCooldownSeconds;
        return MathHelper.clamp(secs, 0, 1200);
    }

    /** Effective post-cycle cooldown as client ticks (20/s); 0 disables the wait entirely. */
    private long effectiveCooldownTicks(Trade trade) {
        return effectiveCooldownSeconds(trade) * 20L;
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

    /** Whether a trade should run at all: ungrouped (or its group was deleted) → yes; in a DISABLED group → no.
     * This is what makes the per-group on/off switch actually pause that group's trades. Static so the iterator can use it. */
    static boolean groupActive(Trade t) {
        if (t.group == null || t.group.isEmpty()) return true;
        var g = CONFIG.client.extra.villagerTrader.groups.get(t.group);
        return g == null || g.enabled;
    }

    private static boolean isUnset(BlockPos p) { return p == null || (p.x() == 0 && p.y() == 0 && p.z() == 0); }

    /**
     * The group a trade draws its INPUT supply from, or null when it uses its own per-trade fields. Null for an earner,
     * an ungrouped/disabled trade, OR a group that has no give-1 chest set yet — that last case lets a group act as a
     * pure organizational on/off bucket (members keep their own chests) until you actually give it a shared supply.
     */
    private @Nullable TradeGroup supplyGroup(Trade trade) {
        if (isEarner(trade)) return null;
        var g = groupOf(trade);
        if (g == null || isUnset(g.inputItem1Chest)) return null;
        return g;
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

    /** Held count of a trade input. Emeralds count BOTH loose emeralds and emerald blocks (9 each), because the
     * restock withdraw pulls either form and CRAFT_EMERALD_BLOCKS uncrafts blocks into spendable emeralds. Counting
     * only loose emeralds made a bot whose supply chest holds emerald BLOCKS believe it had none — so it walked back
     * to the chest every single trade, re-withdrew blocks into an already-stocked inventory, and logged a spurious
     * "Failed restocking sufficient emerald" each cycle. */
    private int heldInputCount(com.aquarius.mc.item.ItemData item) {
        return item == ItemRegistry.EMERALD ? carriedEmeralds() : countItem(item.id());
    }

    /** After a restock pass, can this trade actually do at least one purchase (does it hold each required input)? */
    private boolean tradeInputsAvailable(Trade trade) {
        if (heldInputCount(trade.getInputItem1()) <= 0) return false;
        if (trade.has2InputTrade() && heldInputCount(trade.getInputItem2()) <= 0) return false;
        return true;
    }

    /** A grouped spender that consumes emeralds and is at/below its group's emerald floor (or simply out of emeralds). */
    private boolean needsEmeraldRefill(Trade trade) {
        if (isEarner(trade) || !trade.hasEmeraldInputs()) return false;
        var g = supplyGroup(trade);
        if (g == null) return false; // self-refill only applies within a shared-supply group
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
        cooldownTrade = null;
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
                int input1Count = heldInputCount(input1);
                if (give1RestockThreshold(trade) > input1Count) {
                    setState(State.RESTOCK_INPUT_1_GO_TO_CHEST);
                    return;
                }
                if (trade.has2InputTrade()) {
                    var input2 = ItemRegistry.REGISTRY.get(trade.inputItem2);
                    int input2Count = heldInputCount(input2);
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
                    int input1Count = heldInputCount(input1);
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
                    int input2Count = heldInputCount(input2);
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

                    // Bot-level demand/price pacing: buy out this offer fully only once every
                    // tradeEveryNRestocks DETECTED restocks, skipping the rest so demand can decay between visits
                    // (buying out every restock is the worst case for long-run price — see Trade#priceControlEnabled).
                    if (trade.priceControlEnabled) {
                        if (!priceControlEligible(trade, i)) {
                            maxTradeCount = 0;
                        } else if (maxTradeCount > 0) {
                            markOfferFullyBought(i);
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
                // Cooldown boundary: we just finished the completed trade's segment of the sweep — either it was
                // ungrouped (a trade "by itself"), the next trade belongs to a DIFFERENT group, or the iterator
                // wrapped back to the start. At that point wait the completed trade's effective cooldown (its
                // group's when grouped, else its own) so those villagers can restock before we sweep them again.
                // Mid-group steps and group emerald-earner detours (which route through ENTRYPOINT) don't wait here.
                boolean boundary = trade.group == null || trade.group.isEmpty()
                                || !trade.group.equals(nextTrade.group)
                                || tradeIterator.lastNextWrapped();
                int cooldownSecs = effectiveCooldownSeconds(trade);
                if (boundary && cooldownSecs > 0) {
                    info("Trade segment '{}' complete — cooling down {}s before the next sweep",
                        Objects.requireNonNullElse(getTradeId(trade), "?"), cooldownSecs);
                    cooldownTrade = trade;
                    BARITONE.stop();
                    waitForRestockTimer.reset();
                    setState(State.AWAIT_RESTOCK);
                } else {
                    setState(State.ENTRYPOINT);
                }
            }
            case AWAIT_RESTOCK -> {
                // Idle in place (no pathing churn) until the completed segment's cooldown elapses, then start the
                // next sweep. Re-reads the effective cooldown each tick, so a live edit (even down to 0) takes
                // effect immediately. If the driving trade vanished from config, reset() has already bailed us out.
                long ticks = cooldownTrade != null ? effectiveCooldownTicks(cooldownTrade) : 0L;
                if (waitForRestockTimer.tick(ticks)) {
                    debug("Cycle cooldown elapsed — starting next trade sweep");
                    setState(State.ENTRYPOINT);
                }
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

    /** Record, against every configured trade, which ones this villager's offers can fulfill. Also updates the
     * per-offer demand/restock memory for every matching live offer (not just the first) — a villager can offer
     * more than one live offer matching a single configured trade (e.g. two enchant-level tiers of a book). */
    private void recordVillagerCapabilities(UUID villagerUuid, ClientboundMerchantOffersPacket offers) {
        if (villagerUuid == null || offers == null) return;
        Set<String> caps = new HashSet<>();
        var trades = offers.getTrades();
        for (var entry : CONFIG.client.extra.villagerTrader.trades.entrySet()) {
            var t = entry.getValue();
            if (!t.enabled) continue;
            for (int i = 0; i < trades.length; i++) {
                if (villagerOfferMatchesTrade(trades[i], t)) {
                    caps.add(entry.getKey());
                    updateOfferPriceState(villagerUuid, i, entry.getKey(), trades[i]);
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

    /** Detects a Minecraft-side restock for one live villager offer (numUses reset down since we last looked —
     * there's no dedicated restock packet, this is the only client-observable signal) and keeps the per-offer
     * demand/uses snapshot used by Trade#priceControlEnabled up to date. Tracked for every matching offer
     * regardless of whether price control is enabled, so `.trader price status` has data to show either way.
     * State is keyed by live offer INDEX, which is only a stable identity within a session as long as a villager's
     * trades[] array doesn't reorder/replace an existing slot (new trades append on level-up, so this should hold
     * in practice) — this is verified defensively below rather than assumed, so an index reuse by a DIFFERENT
     * physical offer (e.g. after some server-side trade-list edit) can't misattribute stale numUses history and
     * produce a false restock detection or a wrongly-inherited eligibility budget. */
    private void updateOfferPriceState(UUID villagerUuid, int offerIndex, String tradeId, VillagerTrade offer) {
        var perVillager = offerPriceStateByVillager.computeIfAbsent(villagerUuid, k -> new HashMap<>());
        var state = perVillager.computeIfAbsent(offerIndex, k -> new OfferPriceState());
        int firstInputId = offer.getFirstInput().getId();
        int secondInputId = offer.getSecondInput() != null ? offer.getSecondInput().getId() : -1;
        int outputId = offer.getOutput() != null ? offer.getOutput().getId() : -1;
        boolean identityKnown = state.lastObservedNumUses >= 0;
        boolean sameOffer = identityKnown
            && state.firstInputId == firstInputId
            && state.secondInputId == secondInputId
            && state.outputId == outputId;
        if (identityKnown && !sameOffer) {
            debug("Offer #{} on villager {} no longer matches its previous identity (was input1={},input2={},output={}, "
                + "now input1={},input2={},output={}) — treating as a fresh offer, discarding stale restock history",
                offerIndex, villagerUuid, state.firstInputId, state.secondInputId, state.outputId,
                firstInputId, secondInputId, outputId);
        }
        if (!sameOffer) {
            // Fresh identity (first sighting, or the array slot now holds a different physical offer): reset
            // tracking cleanly instead of comparing numUses across two unrelated offers.
            state.lastObservedNumUses = -1;
            state.restocksSinceLastFullBuy = Integer.MAX_VALUE;
            state.lastRestockDetectedAtMillis = -1;
            state.firstInputId = firstInputId;
            state.secondInputId = secondInputId;
            state.outputId = outputId;
        }
        state.tradeId = tradeId;
        int numUses = offer.getNumUses();
        if (state.lastObservedNumUses >= 0 && numUses < state.lastObservedNumUses) {
            state.restocksSinceLastFullBuy++;
            state.lastRestockDetectedAtMillis = System.currentTimeMillis();
            info("Villager {} restock detected for offer #{} (uses {} -> {}, demand {})",
                villagerUuid, offerIndex, state.lastObservedNumUses, numUses, offer.getDemand());
        }
        state.lastObservedNumUses = numUses;
        state.lastObservedMaxUses = offer.getMaxUses();
        state.lastObservedDemand = offer.getDemand();
    }

    /** Buy-out eligibility for one live offer under Trade#priceControlEnabled: true once at least
     * tradeEveryNRestocks restocks have been DETECTED since the last full buy (or on first-ever sighting — the
     * existing maxInput1PerTrade/maxInput2PerTrade ceiling already guards against overpaying on an unknown offer,
     * so there's no need to also withhold the very first encounter). */
    private boolean priceControlEligible(Trade trade, int offerIndex) {
        var state = offerPriceStateByVillager.getOrDefault(currentVillagerUuid, Map.of()).get(offerIndex);
        return state == null || state.restocksSinceLastFullBuy >= trade.tradeEveryNRestocks;
    }

    /** Marks a live offer as just fully bought out, resetting its restock-skip counter. No-op on first-ever
     * sighting (state==null) since updateOfferPriceState always runs first in the same purchase pass and will
     * have already created it. */
    private void markOfferFullyBought(int offerIndex) {
        var state = offerPriceStateByVillager.getOrDefault(currentVillagerUuid, Map.of()).get(offerIndex);
        if (state != null) state.restocksSinceLastFullBuy = 0;
    }

    /** Wipes ALL per-villager price-control memory. Deliberately NOT wired into reset()/rescan — see the field's
     * doc comment; only ".trader price reset" should call this. */
    public void resetPriceControlState() {
        offerPriceStateByVillager.clear();
    }

    public record PriceStatusEntry(UUID villagerUuid, @Nullable String tradeId, int offerIndex, int numUses,
                                    int maxUses, int demand, int tradeEveryNRestocks, int restocksSinceLastFullBuy,
                                    long msSinceLastRestockDetected) {}

    /** Live snapshot of every tracked (villager, offer) — optionally filtered to one configured trade id — for
     * the ".trader price status" command. Reflects whatever was last observed; a villager not visited since the
     * bot started won't appear. */
    public List<PriceStatusEntry> snapshotPriceStatus(@Nullable String tradeIdFilter) {
        List<PriceStatusEntry> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var villagerEntry : offerPriceStateByVillager.entrySet()) {
            for (var offerEntry : villagerEntry.getValue().entrySet()) {
                var state = offerEntry.getValue();
                if (tradeIdFilter != null && !tradeIdFilter.equals(state.tradeId)) continue;
                var t = state.tradeId != null ? CONFIG.client.extra.villagerTrader.trades.get(state.tradeId) : null;
                out.add(new PriceStatusEntry(villagerEntry.getKey(), state.tradeId, offerEntry.getKey(),
                    state.lastObservedNumUses, state.lastObservedMaxUses, state.lastObservedDemand,
                    t != null ? t.tradeEveryNRestocks : -1, state.restocksSinceLastFullBuy,
                    state.lastRestockDetectedAtMillis < 0 ? -1 : now - state.lastRestockDetectedAtMillis));
            }
        }
        return out;
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

    private static final class OfferPriceState {
        @Nullable String tradeId;
        // Item-identity fingerprint of the physical offer last seen at this index — lets updateOfferPriceState
        // detect when the villager's live offer array no longer has the same offer at this slot (see its doc
        // comment) instead of blindly trusting index stability.
        int firstInputId;
        int secondInputId;
        int outputId;
        int lastObservedNumUses = -1;   // -1 = never observed
        int restocksSinceLastFullBuy = Integer.MAX_VALUE; // "already due" until we know otherwise
        int lastObservedMaxUses;
        int lastObservedDemand;
        long lastRestockDetectedAtMillis = -1;
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
        // True when the most recent next() rolled the index past the end back to 0 — i.e. a full sweep just finished.
        // Only meaningful immediately after a next() call; pointAt()/reset() clear it so a jump isn't read as a wrap.
        boolean lastNextWrapped = false;
        Trade[] backingArray = CONFIG.client.extra.villagerTrader.trades.values()
            .stream()
            .filter(trade -> trade.enabled && groupActive(trade))
            .toArray(Trade[]::new);

        @Override
        public boolean hasNext() {
            return backingArray.length > 0;
        }

        public Trade current() {
            return backingArray[index];
        }

        /** Whether the last {@link #next()} wrapped from the final trade back to the first (a completed sweep). */
        public boolean lastNextWrapped() {
            return lastNextWrapped;
        }

        /** Point the iterator at a specific (enabled) trade so the next cycle runs it. No-op if it isn't in the set. */
        public boolean pointAt(Trade t) {
            for (int i = 0; i < backingArray.length; i++) {
                if (backingArray[i] == t) {
                    index = i;
                    lastNextWrapped = false;
                    return true;
                }
            }
            return false;
        }

        @Override
        public Trade next() {
            if (++index >= backingArray.length) {
                index = 0;
                lastNextWrapped = true;
            } else {
                lastNextWrapped = false;
            }
            return backingArray[index];
        }

        public void refresh() {
            backingArray = CONFIG.client.extra.villagerTrader.trades.values()
                .stream()
                .filter(trade -> trade.enabled && groupActive(trade))
                .toArray(Trade[]::new);
            if (index >= backingArray.length) {
                index = 0;
            }
        }

        public void reset() {
            index = 0;
            lastNextWrapped = false;
            refresh();
        }
    }
}
