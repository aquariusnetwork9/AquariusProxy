package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.enchantment.EnchantmentRegistry;
import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.util.config.Config.Client.Extra.KitMaker.SlotMatch;
import com.aquarius.util.config.Config.Client.Extra.KitMaker.TemplateSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

/**
 * Kit Maker — mass-produce filled kit shulkers from a template + a floor-level chest layout.
 *
 * <p>The kit template is either <b>built in the ABM control plane</b> ({@code client.extra.kitMaker.template}, a
 * slot→item/count/match map — the primary path) or, if that map is empty, read from a designated template chest /
 * an auto-detected placed example shulker (the legacy physical path). It then auto-discovers the surrounding
 * ground-level containers within a radius and classifies them by content (chest of empty shulkers = shulker source,
 * an empty container = finished-kit deposit, the rest = item sources), tallies how many kits the sources can supply,
 * then loops: pull an empty shulker, gather the template's exact items, place + fill a shulker to match, break +
 * collect it, deposit it.
 *
 * <p>Each template slot carries its own match criteria (see {@link SlotMatch}). The {@code ByEnchants} mode is the
 * independent, separately-enforced rule that keeps a Silk Touch pickaxe and a Fortune pickaxe as distinct needs:
 * a source item matches a slot iff it is the same item AND carries every enchant the slot requires.
 *
 * <p>This is a port of MelonKit's {@code KitFiller} onto the AquariusProxy API. Block-breaking is forbidden for the
 * whole run except the {@code BREAK_SHULKER} phase (via {@link #setBreakingAllowed}), so the bot never digs the floor
 * while pathing between chests. While a container is open the standalone inventory cache is stale (the bug the miner
 * hit on 2b2t), so gather/fill read the OPEN window's player slots, never container 0.
 */
public class KitMaker extends AbstractFieldModule {

    private enum State {
        IDLE, SCAN_TEMPLATE, SCAN_LAYOUT, ENSURE_SHULKER, GATHER, PLACE_SHULKER, FILL, BREAK_SHULKER, DEPOSIT, DONE
    }

    /**
     * One slot of the resolved kit template: the container-half slot index, the wanted item id + count, the slot's
     * effective match mode, the required enchant registry ids (for {@link SlotMatch#ByEnchants}), and — for the
     * legacy physical path — a {@code sample} stack whose full components drive Smart/Exact/Auto matching.
     */
    private record KitSlot(int index, int itemId, int count, SlotMatch match, List<Integer> reqEnch, @Nullable ItemStack sample) {}
    /** An aggregated material need: a representative slot (carries the matcher) and the total count the kit requires. */
    private record Need(KitSlot slot, int count) {}

    private State state = State.IDLE;
    private int step;
    private int timer;
    private int attempts;
    private @Nullable GoalNear pathGoal;

    // setup
    private final List<KitSlot> template = new ArrayList<>();
    private @Nullable BlockPos shulkerSrc;
    private final List<BlockPos> itemSrcs = new ArrayList<>();
    private @Nullable BlockPos depositChest;
    private final List<BlockPos> scanQueue = new ArrayList<>();   // candidates left to classify
    private int srcIdx;                                           // which item source during GATHER
    private int fillIdx;                                          // which template slot during FILL

    // runtime
    private @Nullable BlockPos templatePos;                       // resolved template block (explicit coords or auto-detected shulker)
    private @Nullable BlockPos placedShulker;
    private @Nullable BlockPos pendingPlace;
    private @Nullable BlockPos lastPlaceSpot;
    private int cyclesDone;
    private String doneReason = "stopped";

    // status (surfaced to the ABM control plane via statusJson())
    private String lastError = "";
    private String lastErrorKind = "";                            // "" | shulkerEmpty | missingSupply | depositFull | setup | other
    private int kitsBuildable = -1;                               // COMPLETE kits from the last layout scan; -1 = unknown
    private double nextKitFillable = 0.0;                         // fraction of the NEXT kit's slots the leftovers could fill
    private final List<Map<String, Object>> shortfalls = new ArrayList<>(); // next-kit shortfalls [{item,have,need,short,enchants}]
    private @Nullable List<Need> needsCache;                      // needs for the current run (for supply tally)
    private long @Nullable [] availPerNeed;                       // accumulated source counts per need (parallel to needsCache)

    private boolean paused;
    private boolean complete;
    private boolean hazardPaused;

    @Override
    public boolean enabledSetting() { return CONFIG.client.extra.kitMaker.enabled; }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, this::onStarting)
        );
    }

    @Override
    public void onEnable() {
        paused = false; complete = false; hazardPaused = false;
        cyclesDone = 0;
        template.clear(); shulkerSrc = null; itemSrcs.clear(); depositChest = null; scanQueue.clear();
        placedShulker = null; pendingPlace = null; lastPlaceSpot = null; templatePos = null;
        lastError = ""; lastErrorKind = ""; kitsBuildable = -1; nextKitFillable = 0.0; shortfalls.clear(); needsCache = null; availPerNeed = null;
        setBreakingAllowed(false);                 // forbidden until a harvest phase
        go(State.SCAN_TEMPLATE);
        info("Kit Maker: reading the template + scanning the chest layout.");
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        state = State.IDLE;
    }

    private void onStarting(ClientBotTick.Starting event) {
        // (re)connected: re-scan the layout from a clean state if we were mid-run.
        if (state != State.IDLE && !complete) { onEnable(); }
    }

    private void go(State s) { state = s; step = 0; timer = 0; attempts = 0; }

    private void stop(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        complete = true;
        state = State.IDLE;
        setError(reason);
        info("Kit Maker: {} ({} kits made).", reason, cyclesDone);
        inGameAlertActivePlayer("<green>Kit Maker done: " + reason + " (" + cyclesDone + " kits)");
        CONFIG.client.extra.kitMaker.enabled = false;
        syncEnabledFromConfig();
        if (CONFIG.client.extra.kitMaker.autoDisconnect) {
            executeCommand("disconnect", true);
        }
    }

    private void abort(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        paused = true;
        state = State.IDLE;
        setError(reason);
        warn("Kit Maker paused: {}. Fix the setup and toggle /kitmaker off/on.", reason);
        inGameAlertActivePlayer("<red>Kit Maker paused: " + reason);
    }

    /** Record a human reason + a coarse machine kind for the ABM Status panel. */
    private void setError(String reason) {
        lastError = reason == null ? "" : reason;
        lastErrorKind = classifyKind(lastError);
    }

    private String classifyKind(String reason) {
        String r = reason == null ? "" : reason.toLowerCase();
        if (r.isEmpty() || r.contains("reached max") || r.equals("stopped")) return "";
        if (r.contains("no empty shulker") || r.contains("no chest of empty") || r.contains("empty shulkers left")) return "shulkerEmpty";
        if (r.contains("deposit") && r.contains("full")) return "depositFull";
        if (r.contains("not enough items") || r.contains("ran out of") || r.contains("no item-source")) return "missingSupply";
        if (r.contains("template")) return "setup";
        return "other";
    }

    // ---------------------------------------------------------------- tick

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.kitMaker;
        if (notReady()) return;
        if (state == State.IDLE || complete || paused) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (!hazardPaused) {
                hazardPaused = true;
                if (BARITONE.isActive()) BARITONE.stop();
                warn("Kit Maker: player within {} blocks - pausing.", (int) cfg.playerPauseRange);
            }
            return;
        }
        if (hazardPaused) { hazardPaused = false; info("Kit Maker: clear - resuming."); }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case SCAN_TEMPLATE -> tickScanTemplate();
            case SCAN_LAYOUT -> tickScanLayout();
            case ENSURE_SHULKER -> tickEnsureShulker();
            case GATHER -> tickGather();
            case PLACE_SHULKER -> tickPlaceShulker();
            case FILL -> tickFill();
            case BREAK_SHULKER -> tickBreakShulker();
            case DEPOSIT -> tickDeposit();
            case DONE -> stop(doneReason);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- setup: template + layout

    private void tickScanTemplate() {
        var cfg = CONFIG.client.extra.kitMaker;
        // Primary path: a kit selected/built in the control plane (the active library kit, or legacy template).
        var slotMap = activeSlotMap();
        if (slotMap != null) {
            buildTemplateFromSlots(slotMap);
            if (template.isEmpty()) { abort("the selected kit has no valid items - build one in the dashboard"); return; }
            info("Kit Maker: using {} ({} slots).", activeKitLabel(), template.size());
            go(State.SCAN_LAYOUT);
            return;
        }
        switch (step) {
            case 0 -> {
                // Legacy physical path: resolve the template block once (explicit coords if set, else the nearest
                // PLACED shulker box near the bot, so you can just plonk one down).
                if (templatePos == null) {
                    if (cfg.templateChestX == 0 && cfg.templateChestY == 0 && cfg.templateChestZ == 0) {
                        BlockPos pf = playerFeet();
                        templatePos = nearestPlacedShulker(pf, cfg.scanRadius);
                        if (templatePos == null) {
                            abort("no template set and no placed shulker box found nearby - build a kit in the dashboard, place an example shulker, or use .km template <x> <y> <z>"); return;
                        }
                        info("Kit Maker: auto-detected template shulker @ {}.", templatePos);
                    } else {
                        templatePos = new BlockPos(cfg.templateChestX, cfg.templateChestY, cfg.templateChestZ);
                    }
                }
                if (!arrivedAt(new GoalNear(templatePos, REACH_RANGE_SQ))) { pathGoal = pathToNear(templatePos); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(templatePos); timer = cfg.settleTicks; step = 1;
            }
            default -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { abort("template container wouldn't open"); return; }
                    open(templatePos); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                if (c == null) { closeContainer(); abort("template container read failed"); return; }
                // A PLACED shulker box is read in place (its 27 slots ARE the template). A chest/barrel must instead
                // hold an example shulker ITEM, whose contents become the template.
                boolean placedShulkerTemplate = com.aquarius.feature.player.World
                    .getBlock(templatePos.x(), templatePos.y(), templatePos.z()).name().endsWith("shulker_box");
                if (placedShulkerTemplate) {
                    buildTemplateFromContainer(c);
                } else {
                    int slot = findContainerSlot(c, this::isFilledShulker);
                    if (slot == -1) { closeContainer(); abort("template chest holds no example (filled) kit shulker"); return; }
                    buildTemplate(c.getItemStack(slot));
                }
                closeContainer();
                if (template.isEmpty()) { abort("the template is empty"); return; }
                captureCurrentTemplateToConfig();   // surface the auto-detected kit so the dashboard can import + edit it
                info("Kit Maker: template = {} filled slots (from {}).", template.size(),
                    placedShulkerTemplate ? "placed shulker" : "chest example");
                go(State.SCAN_LAYOUT);
            }
        }
    }

    /** Which slot map the module should build: the active library kit, else the legacy template, else null = physical. */
    private @Nullable Map<String, TemplateSlot> activeSlotMap() {
        var cfg = CONFIG.client.extra.kitMaker;
        String ak = cfg.activeKit == null ? "" : cfg.activeKit.trim();
        if (ak.equals("__auto")) return null;                       // explicit physical auto-detect
        if (!ak.isEmpty()) {
            var kd = cfg.kits.get(ak);
            if (kd != null && kd.slots != null && !kd.slots.isEmpty()) return kd.slots;
            // active points at a missing/empty kit → fall through to legacy template / physical
        }
        if (!cfg.template.isEmpty()) return cfg.template;
        return null;
    }

    private String activeKitLabel() {
        var cfg = CONFIG.client.extra.kitMaker;
        String ak = cfg.activeKit == null ? "" : cfg.activeKit.trim();
        if (!ak.isEmpty() && !ak.equals("__auto") && cfg.kits.containsKey(ak)) return "kit '" + ak + "'";
        return "the configured template";
    }

    /** Build the runtime template from a control-plane slot map (slot index → item/count/match/enchants). */
    private void buildTemplateFromSlots(Map<String, TemplateSlot> slots) {
        template.clear();
        for (var e : slots.entrySet()) {
            int idx;
            try { idx = Integer.parseInt(e.getKey().trim()); } catch (NumberFormatException ex) { continue; }
            if (idx < 0) continue;
            var ts = e.getValue();
            if (ts == null || ts.item == null || ts.item.isBlank()) continue;
            ItemData d = ItemRegistry.REGISTRY.get(stripNs(ts.item));
            if (d == null) { warn("Kit Maker: unknown item '{}' in slot {} - skipped.", ts.item, idx); continue; }
            int count = Math.max(1, Math.min(ts.count <= 0 ? 1 : ts.count, d.stackSize()));
            SlotMatch match = ts.match == null ? SlotMatch.Auto : ts.match;
            List<Integer> reqEnch = new ArrayList<>();
            if (match == SlotMatch.ByEnchants && ts.enchants != null) {
                for (String en : ts.enchants) {
                    if (en == null || en.isBlank()) continue;
                    var ed = EnchantmentRegistry.REGISTRY.get(stripNs(en));
                    if (ed != null) reqEnch.add(ed.id());
                    else warn("Kit Maker: unknown enchant '{}' in slot {} - ignored.", en, idx);
                }
            }
            template.add(new KitSlot(idx, d.id(), count, match, reqEnch, null));
        }
        template.sort(Comparator.comparingInt(KitSlot::index));
    }

    /** Mirror the just-built physical template into {@code kitMaker.captured} (with detected enchants) so the
     *  dashboard can import an auto-detected kit into the builder, edit it, and save it to the library. */
    private void captureCurrentTemplateToConfig() {
        var cfg = CONFIG.client.extra.kitMaker;
        LinkedHashMap<String, TemplateSlot> cap = new LinkedHashMap<>();
        for (KitSlot ks : template) {
            ItemData d = ItemRegistry.REGISTRY.get(ks.itemId());
            if (d == null) continue;
            TemplateSlot ts = new TemplateSlot();
            ts.item = d.name();
            ts.count = ks.count();
            List<String> ench = ks.sample() != null ? detectEnchants(ks.sample()) : List.of();
            if (!ench.isEmpty()) { ts.match = SlotMatch.ByEnchants; ts.enchants = new ArrayList<>(ench); }
            else { ts.match = SlotMatch.Auto; ts.enchants = new ArrayList<>(); }
            cap.put(String.valueOf(ks.index()), ts);
        }
        cfg.captured = cap;
        com.aquarius.Globals.saveConfigAsync();
        info("Kit Maker: captured {} detected slots for the dashboard import.", cap.size());
    }

    /** All enchant ids present on an item (held or stored), mapped back to their registry names. */
    private List<String> detectEnchants(ItemStack s) {
        List<String> out = new ArrayList<>();
        addEnchNames(out, s, DataComponentTypes.ENCHANTMENTS);
        addEnchNames(out, s, DataComponentTypes.STORED_ENCHANTMENTS);
        return out;
    }

    private void addEnchNames(List<String> out, ItemStack s, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments e = s.getDataComponentsOrEmpty().get(type);
        if (e == null) return;
        for (int id : e.getEnchantments().keySet()) {
            var ed = EnchantmentRegistry.REGISTRY.get(id);
            if (ed != null && !out.contains(ed.name())) out.add(ed.name());
        }
    }

    private String stripNs(String s) { int i = s.indexOf(':'); return (i >= 0 ? s.substring(i + 1) : s).trim().toLowerCase(); }

    /** Build the template directly from an OPEN container's chest slots (a placed shulker box read in place). */
    private void buildTemplateFromContainer(Container c) {
        template.clear();
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (isRealItem(s)) template.add(slotFromSample(i, s));
        }
    }

    /** Nearest placed shulker-box block to {@code from} within {@code radius} (±3 Y), or null. */
    private @Nullable BlockPos nearestPlacedShulker(BlockPos from, int radius) {
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos b : findBlocks(radius, from.y() - 3, from.y() + 3, n -> n.endsWith("shulker_box"))) {
            long dx = b.x() - from.x(), dy = b.y() - from.y(), dz = b.z() - from.z();
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /** Build the per-slot template from a shulker item's positional CONTAINER component (index = slot).
     *  Empty slots come through as air stacks (NOT the null EMPTY_STACK sentinel), so they must be filtered by
     *  air-id / zero-count — otherwise a 16-item kit yields a 27-slot template with 11 phantom "air" needs that no
     *  source can ever satisfy, and every run dies with "not enough items for a full kit". */
    private void buildTemplate(@Nullable ItemStack shulker) {
        template.clear();
        List<ItemStack> contents = containerContents(shulker);
        for (int i = 0; i < contents.size(); i++) {
            ItemStack s = contents.get(i);
            if (isRealItem(s)) template.add(slotFromSample(i, s));
        }
    }

    /** A physical-path slot: matches by the global mode against the real captured sample (Auto + sample). */
    private KitSlot slotFromSample(int index, ItemStack s) {
        return new KitSlot(index, s.getId(), s.getAmount(), SlotMatch.Auto, List.of(), s);
    }

    /** A genuine, non-empty item (not the null sentinel, not air, positive count). */
    private boolean isRealItem(@Nullable ItemStack s) {
        return s != Container.EMPTY_STACK && s.getAmount() > 0 && s.getId() != ItemRegistry.AIR.id();
    }

    private void tickScanLayout() {
        var cfg = CONFIG.client.extra.kitMaker;
        switch (step) {
            case 0 -> {                                   // discover floor-level container blocks once
                needsCache = aggregateNeeds();            // freeze the run's needs so the supply tally can use them
                availPerNeed = new long[needsCache.size()];
                BlockPos pf = playerFeet();
                List<BlockPos> raw = findBlocks(cfg.scanRadius, pf.y() - cfg.floorBandDown, pf.y() + cfg.floorBandUp, this::isContainerBlockName);
                scanQueue.clear();
                for (BlockPos b : raw) {
                    if (b.equals(templatePos)) continue;  // never consume the template container
                    // Deliberately NO double-chest dedup: visiting both halves of a double is harmless (they share
                    // one inventory — the second open just finds it already drained / a second pickup chance), while
                    // any adjacency dedup silently drops the MIDDLE container in a row of singles (or same-facing
                    // doubles). With mixed singles/doubles + random rotation, "classify everything" is the only safe rule.
                    scanQueue.add(b);
                }
                if (scanQueue.isEmpty()) { abort("no floor-level containers found within " + cfg.scanRadius + " blocks"); return; }
                info("Kit Maker: classifying {} containers.", scanQueue.size());
                step = 1;
            }
            case 1 -> {                                   // path to + open the next candidate
                if (scanQueue.isEmpty()) { finishLayout(); return; }
                BlockPos cand = scanQueue.get(0);
                if (!arrivedAt(new GoalNear(cand, REACH_RANGE_SQ))) { pathGoal = pathToNear(cand); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(cand); timer = cfg.settleTicks; step = 2; attempts = 0;
            }
            default -> {                                  // classify, close, advance
                BlockPos cand = scanQueue.get(0);
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { scanQueue.remove(0); step = 1; return; }   // skip unreachable
                    open(cand); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                if (c != null) classify(cand, c);
                closeContainer();
                scanQueue.remove(0);
                step = 1; timer = cfg.actionDelayTicks;
            }
        }
    }

    /** Classify a container by its contents: empty-shulker store, finished-kit deposit, or item source. */
    private void classify(BlockPos pos, Container c) {
        boolean anyEmptyShulker = false, anyNonShulker = false;
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == Container.EMPTY_STACK) continue;
            if (isEmptyShulker(s)) anyEmptyShulker = true;
            else if (!isShulkerBox(s)) anyNonShulker = true;   // a real material (filled kit shulkers are neither)
        }
        // Priority: a chest with real materials is an item source; one with empty shulkers is the shulker supply;
        // anything else (empty, OR holding only FINISHED kit shulkers) is the deposit — the deposit naturally fills
        // with kits across runs, so it must NOT be required to be empty (that broke every re-run).
        if (anyNonShulker)                          { itemSrcs.add(pos); tallySupply(c); info("Kit Maker:  item source @ {}", pos); return; }
        if (anyEmptyShulker) { if (shulkerSrc == null) { shulkerSrc = pos; info("Kit Maker:  shulker source @ {}", pos); } return; }
        if (depositChest == null)                   { depositChest = pos; info("Kit Maker:  kit deposit @ {}", pos); }
    }

    /** Accumulate how many of each need an item-source chest holds (drives the "kits buildable" estimate). */
    private void tallySupply(Container c) {
        if (needsCache == null || availPerNeed == null) return;
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == Container.EMPTY_STACK) continue;
            for (int n = 0; n < needsCache.size(); n++) {
                if (matchesSlot(s, needsCache.get(n).slot())) { availPerNeed[n] += s.getAmount(); break; }
            }
        }
    }

    private void finishLayout() {
        computeEstimate();
        if (shulkerSrc == null) { abort("no chest of empty shulkers found (the shulker source)"); return; }
        if (depositChest == null) { abort("no empty chest found for finished kits (the deposit)"); return; }
        if (itemSrcs.isEmpty()) { abort("no item-source chests found"); return; }
        info("Kit Maker: layout OK - {} item sources, ~{} kits buildable. Making kits.", itemSrcs.size(), kitsBuildable);
        go(State.ENSURE_SHULKER);
    }

    /**
     * From the frozen needs + tallied supply, compute the number of COMPLETE kits buildable (the limiting material),
     * then the shortfalls + slot-fill fraction for the NEXT kit. Partial kits never inflate the count: a kit only
     * counts when every slot can be filled, so leftover scraps (a few armor + a tool, < the limiting material) don't
     * show as another kit.
     */
    private void computeEstimate() {
        shortfalls.clear();
        nextKitFillable = 0.0;
        if (needsCache == null || availPerNeed == null || needsCache.isEmpty()) { kitsBuildable = -1; return; }

        // complete kits = limited by the scarcest material
        long full = Long.MAX_VALUE;
        for (int n = 0; n < needsCache.size(); n++) {
            int per = Math.max(1, needsCache.get(n).count());
            full = Math.min(full, availPerNeed[n] / per);
        }
        int kits = (full == Long.MAX_VALUE) ? 0 : (int) Math.min(Integer.MAX_VALUE, full);
        int maxKits = CONFIG.client.extra.kitMaker.maxKits;
        if (maxKits > 0) kits = Math.min(kits, Math.max(0, maxKits - cyclesDone));
        kitsBuildable = kits;

        // what's left for the NEXT (kits+1) kit, and what it's short
        long[] rem = new long[needsCache.size()];
        for (int n = 0; n < needsCache.size(); n++) {
            rem[n] = availPerNeed[n] - (long) kits * Math.max(1, needsCache.get(n).count());
            if (rem[n] < 0) rem[n] = 0;
            Need need = needsCache.get(n);
            int per = Math.max(1, need.count());
            if (rem[n] < per) {
                Map<String, Object> sf = new LinkedHashMap<>();
                sf.put("item", itemNameOf(need.slot()));
                sf.put("have", rem[n]);
                sf.put("need", per);
                sf.put("short", per - rem[n]);
                sf.put("enchants", enchNames(need.slot()));
                shortfalls.add(sf);
            }
        }

        // fraction of the kit's SLOTS the leftovers could fill (greedy per need) — drives the 30% partial rule
        long[] r2 = rem.clone();
        int fill = 0, total = template.size();
        for (KitSlot ks : template) {
            int n = needIndexOf(ks);
            if (n >= 0 && r2[n] >= ks.count()) { fill++; r2[n] -= ks.count(); }
        }
        nextKitFillable = total > 0 ? (double) fill / total : 0.0;
    }

    private int needIndexOf(KitSlot ks) {
        if (needsCache == null) return -1;
        for (int n = 0; n < needsCache.size(); n++) if (sameNeed(needsCache.get(n).slot(), ks)) return n;
        return -1;
    }

    // ---------------------------------------------------------------- per-kit loop

    private void tickEnsureShulker() {
        var cfg = CONFIG.client.extra.kitMaker;
        if (cfg.maxKits > 0 && cyclesDone >= cfg.maxKits) { doneReason = "reached max kits (" + cfg.maxKits + ")"; go(State.DONE); return; }
        switch (step) {
            case 0 -> {
                if (countInInv(this::isEmptyShulker) > 0) { srcIdx = 0; go(State.GATHER); return; }
                if (!arrivedAt(new GoalNear(shulkerSrc, REACH_RANGE_SQ))) { pathGoal = pathToNear(shulkerSrc); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(shulkerSrc); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { abort("shulker source wouldn't open"); return; }
                    open(shulkerSrc); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                int src = c == null ? -1 : findContainerSlot(c, this::isEmptyShulker);
                if (src == -1) { closeContainer(); doneReason = "no empty shulkers left in the source"; go(State.DONE); return; }
                if (!inventoryBusy()) shiftClick(c, src);
                timer = cfg.fillDelayTicks; step = 2;
            }
            default -> {   // wait for the pulled shulker to land in the OPEN window, then close + gather
                Container c = openContainer();
                if (c != null && findPlayerWindowSlot(c, this::isEmptyShulker) != -1) { closeContainer(); srcIdx = 0; go(State.GATHER); }
                else if (++attempts >= 12) { abort("pulled a shulker but it didn't reach the inventory"); }
                else timer = cfg.fillDelayTicks;
            }
        }
    }

    private void tickGather() {
        var cfg = CONFIG.client.extra.kitMaker;
        switch (step) {
            case 0 -> {
                if (allNeedsMet()) { if (openContainerId() != 0) closeContainer(); goPlaceShulker(); return; }
                if (srcIdx >= itemSrcs.size()) {
                    if (openContainerId() != 0) closeContainer();
                    // Sources exhausted without a full kit. Build a best-effort PARTIAL kit only if allowed AND we
                    // gathered at least `partialMinSlotFraction` of the kit's slots — a few scraps don't get built
                    // (or counted) as a near-empty kit. Below that threshold, stop instead of looping.
                    double frac = gatheredSlotFraction();
                    if (cfg.allowPartial && frac >= cfg.partialMinSlotFraction) {
                        info("Kit Maker: sources short - building a partial kit ({}% of slots filled).", (int) Math.round(frac * 100));
                        goPlaceShulker(); return;
                    }
                    doneReason = "not enough items in the sources for a kit"; go(State.DONE); return;
                }
                BlockPos src = itemSrcs.get(srcIdx);
                if (!arrivedAt(new GoalNear(src, REACH_RANGE_SQ))) { pathGoal = pathToNear(src); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(src); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { srcIdx++; step = 0; return; }     // skip unreachable source
                    open(itemSrcs.get(srcIdx)); timer = cfg.settleTicks; return;
                }
                step = 2;
            }
            default -> {   // pull every still-needed item this chest holds, one stack per tick
                if (openContainerId() == 0) { step = 0; return; }
                Container c = openContainer();
                if (c == null) { timer = cfg.fillDelayTicks; return; }
                List<Need> needs = aggregateNeeds();
                int chestSlots = Math.max(0, c.getSize() - 36);
                int slot = -1;
                for (int i = 0; i < chestSlots; i++) {
                    ItemStack cs = c.getItemStack(i);
                    if (cs != Container.EMPTY_STACK && windowHasRoomFor(c, cs) && satisfiesUnmetNeed(cs, needs)) { slot = i; break; }
                }
                if (slot != -1) { if (!inventoryBusy()) shiftClick(c, slot); timer = cfg.fillDelayTicks; }
                else { closeContainer(); srcIdx++; step = 0; timer = cfg.actionDelayTicks; }   // nothing more needed here
            }
        }
    }

    /** Enter PLACE_SHULKER fresh. */
    private void goPlaceShulker() { lastPlaceSpot = null; go(State.PLACE_SHULKER); }

    private void tickPlaceShulker() {
        var cfg = CONFIG.client.extra.kitMaker;
        switch (step) {
            case 0 -> {
                pendingPlace = selectSpotBeside(lastPlaceSpot);
                if (pendingPlace == null) pendingPlace = selectSpotBeside(null);   // only the failed spot is open
                if (pendingPlace == null) {
                    if (++attempts >= 8) { abort("no open block beside the bot to place a shulker"); return; }
                    timer = cfg.actionDelayTicks * 2; return;
                }
                int slot = findInInv(this::isEmptyShulker);
                if (slot == -1) { go(State.ENSURE_SHULKER); return; }
                place(pendingPlace, itemDataAt(slot));
                lastPlaceSpot = pendingPlace;
                timer = cfg.placeVerifyTicks; step = 1;
            }
            default -> {
                // the spot was air before placing (selectSpotBeside requires it), so a non-air block there now is our shulker
                if (placed(pendingPlace)) { placedShulker = pendingPlace; attempts = 0; lastPlaceSpot = null; go(State.FILL); return; }
                if (++attempts >= 8) { abort("shulker placement kept failing (server rejected / spot blocked)"); return; }
                step = 0; timer = cfg.actionDelayTicks;
            }
        }
    }

    /** FILL: deposit each template slot to its exact item + count. One click per tick, re-derived from live state. */
    private void tickFill() {
        var cfg = CONFIG.client.extra.kitMaker;
        switch (step) {
            case 0 -> {
                if (!placed(placedShulker)) { placedShulker = null; goPlaceShulker(); return; }   // vanished -> re-place
                open(placedShulker); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() != 0) { fillIdx = 0; step = 2; timer = cfg.fillDelayTicks; }
                else if (++attempts >= 6) abort("placed shulker wouldn't open");
                else { open(placedShulker); timer = cfg.settleTicks; }
            }
            default -> fillStep();
        }
    }

    private void fillStep() {
        var cfg = CONFIG.client.extra.kitMaker;
        if (openContainerId() == 0) { abort("shulker closed unexpectedly while filling"); return; }
        if (inventoryBusy()) { timer = cfg.fillDelayTicks; return; }
        Container c = openContainer();
        if (c == null) { timer = cfg.fillDelayTicks; return; }

        if (fillIdx >= template.size()) {                          // done: stash any leftover cursor, then break
            if (!cursorEmpty()) { stashCursor(c); timer = cfg.fillDelayTicks; return; }
            closeContainer(); go(State.BREAK_SHULKER); return;
        }

        KitSlot ks = template.get(fillIdx);
        int idx = ks.index();
        int needed = ks.count();

        ItemStack inSlot = c.getItemStack(idx);
        int current;
        if (inSlot == Container.EMPTY_STACK) current = 0;
        else if (matchesSlot(inSlot, ks)) current = inSlot.getAmount();
        else { abort("shulker slot " + idx + " already holds a different item"); return; }

        if (current >= needed) {                                   // slot satisfied
            if (!cursorEmpty()) { stashCursor(c); timer = cfg.fillDelayTicks; return; }
            fillIdx++; timer = cfg.fillDelayTicks; return;
        }

        ItemStack cursor = mouseStack();
        if (cursor == Container.EMPTY_STACK || !matchesSlot(cursor, ks)) {
            if (cursor != Container.EMPTY_STACK) { stashCursor(c); timer = cfg.fillDelayTicks; return; }  // wrong item held
            int src = findPlayerWindowSlot(c, s -> matchesSlot(s, ks));
            if (src == -1) {
                if (cfg.allowPartial) { fillIdx++; timer = cfg.fillDelayTicks; return; }   // partial kit: leave slot empty, next
                doneReason = "ran out of " + itemNameOf(ks) + " while filling"; closeContainer(); go(State.DONE); return;
            }
            leftClick(c, src);                                     // grab the whole player stack onto the cursor
            timer = cfg.fillDelayTicks; return;
        }

        // cursor holds the right item: deposit into idx
        if (current == 0 && cursor.getAmount() == needed) leftClick(c, idx);   // whole cursor into empty slot (one action)
        else rightClick(c, idx);                                                // one at a time (partial counts)
        timer = cfg.fillDelayTicks;
    }

    /** Drop the held cursor stack into the first empty player-window slot. */
    private void stashCursor(Container c) {
        int empty = findEmptyPlayerWindowSlot(c);
        if (empty != -1) leftClick(c, empty);
    }

    private void tickBreakShulker() {
        var cfg = CONFIG.client.extra.kitMaker;
        if (step == 0) { setBreakingAllowed(true); step = 1; attempts = 0; }     // harvest phase: breaking ON
        if (placed(placedShulker)) {
            if (++attempts > 200) { setBreakingAllowed(false); abort("couldn't break the filled shulker"); return; }
            breakAt(placedShulker, true);                                        // continuous, every tick
            return;
        }
        // block gone -> collect the dropped filled shulker, then breaking OFF again
        if (!BARITONE.isActive()) BARITONE.pickup();
        boolean collected = countInInv(this::isFilledShulker) > 0;
        if (collected || ++step > 60) {
            if (BARITONE.isActive()) BARITONE.stop();
            setBreakingAllowed(false);
            placedShulker = null;
            go(State.DEPOSIT);
        } else {
            timer = cfg.fillDelayTicks;
        }
    }

    private void tickDeposit() {
        var cfg = CONFIG.client.extra.kitMaker;
        switch (step) {
            case 0 -> {
                if (!arrivedAt(new GoalNear(depositChest, REACH_RANGE_SQ))) { pathGoal = pathToNear(depositChest); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(depositChest); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() != 0) { step = 2; attempts = 0; }
                else if (++attempts >= 6) abort("deposit chest wouldn't open");
                else { open(depositChest); timer = cfg.settleTicks; }
            }
            default -> {
                Container c = openContainer();
                if (c == null) { step = 0; return; }
                int src = findPlayerWindowSlot(c, this::isFilledShulker);
                if (src == -1) {                                   // all finished kits deposited
                    closeContainer();
                    cyclesDone++;
                    info("Kit Maker: kit #{} deposited.", cyclesDone);
                    go(State.ENSURE_SHULKER);
                    return;
                }
                if (containerFull(c)) { closeContainer(); doneReason = "deposit chest is full"; go(State.DONE); return; }
                if (!inventoryBusy()) shiftClick(c, src);
                timer = cfg.fillDelayTicks;
            }
        }
    }

    private boolean containerFull(Container c) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) if (c.getItemStack(i) == Container.EMPTY_STACK) return false;
        return true;
    }

    // ---------------------------------------------------------------- needs / matching

    private List<Need> aggregateNeeds() {
        List<Need> needs = new ArrayList<>();
        for (KitSlot ks : template) {
            int found = -1;
            for (int i = 0; i < needs.size(); i++) if (sameNeed(needs.get(i).slot(), ks)) { found = i; break; }
            if (found == -1) needs.add(new Need(ks, ks.count()));
            else { Need n = needs.get(found); needs.set(found, new Need(n.slot(), n.count() + ks.count())); }
        }
        return needs;
    }

    /** Two slots aggregate into one need iff a candidate that matches one matches the other identically. */
    private boolean sameNeed(KitSlot a, KitSlot b) {
        if (a.itemId() != b.itemId()) return false;
        if (a.match() != b.match()) return false;
        if (a.match() == SlotMatch.ByEnchants) return new HashSet<>(a.reqEnch()).equals(new HashSet<>(b.reqEnch()));
        if (a.sample() != null && b.sample() != null) return matchesGlobal(a.sample(), b.sample());
        if (a.sample() != null || b.sample() != null) return false;
        return true;   // both config, same id + same (Auto/ByType/Exact-without-sample) mode
    }

    private boolean allNeedsMet() {
        for (Need need : aggregateNeeds()) if (liveInvCount(need.slot()) < need.count()) return false;
        return true;
    }

    /** True if the inventory holds at least one item that any template slot wants (a partial kit is worth building). */
    private boolean gatheredAnyTemplateItem() {
        for (Need need : aggregateNeeds()) if (liveInvCount(need.slot()) > 0) return true;
        return false;
    }

    /** Fraction of the kit's slots the currently-gathered inventory could fill (greedy per need) — the 30% partial gate. */
    private double gatheredSlotFraction() {
        if (template.isEmpty()) return 0.0;
        List<Need> needs = aggregateNeeds();
        long[] rem = new long[needs.size()];
        for (int n = 0; n < needs.size(); n++) rem[n] = liveInvCount(needs.get(n).slot());
        int fill = 0;
        for (KitSlot ks : template) {
            int idx = -1;
            for (int n = 0; n < needs.size(); n++) if (sameNeed(needs.get(n).slot(), ks)) { idx = n; break; }
            if (idx >= 0 && rem[idx] >= ks.count()) { fill++; rem[idx] -= ks.count(); }
        }
        return (double) fill / template.size();
    }

    private boolean satisfiesUnmetNeed(ItemStack candidate, List<Need> needs) {
        if (candidate == Container.EMPTY_STACK) return false;
        for (Need need : needs) if (matchesSlot(candidate, need.slot()) && liveInvCount(need.slot()) < need.count()) return true;
        return false;
    }

    /** Count of items matching {@code slot} carried in the player inventory (all 36 slots — a shift-click pull
     *  from a chest fills the hotbar first, so the hotbar must be counted). Reads the OPEN window's player slots
     *  while a container is open (the standalone cache is stale then), else the standalone inventory (9-44). */
    private int liveInvCount(KitSlot slot) {
        int n = 0;
        if (openContainerId() != 0) {
            Container c = openContainer();
            if (c == null) return 0;
            int size = c.getSize();
            for (int i = size - 36; i < size; i++) { ItemStack s = c.getItemStack(i); if (matchesSlot(s, slot)) n += s.getAmount(); }
        } else {
            List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
            for (int i = 9; i <= 44; i++) { ItemStack s = inv.get(i); if (matchesSlot(s, slot)) n += s.getAmount(); }
        }
        return n;
    }

    /** Room to receive a gathered stack somewhere in the OPEN window's player inventory (all 36 slots). */
    private boolean windowHasRoomFor(Container c, ItemStack s) {
        int size = c.getSize();
        for (int i = size - 36; i < size; i++) {
            ItemStack m = c.getItemStack(i);
            if (m == Container.EMPTY_STACK) return true;
            if (maxStack(s) > 1 && matches(m, s) && m.getAmount() < maxStack(s)) return true;
        }
        return false;
    }

    private int maxStack(ItemStack s) {
        ItemData d = ItemRegistry.REGISTRY.get(s.getId());
        return d == null ? 64 : d.stackSize();
    }

    /** Plain same-item check (id only) — used when stacking gathered items in the player window. */
    private boolean matches(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == Container.EMPTY_STACK || b == Container.EMPTY_STACK || a == null || b == null) return false;
        return a.getId() == b.getId();
    }

    /**
     * Does a candidate item satisfy a template slot, per the slot's EFFECTIVE match criteria? Count is ignored here.
     * This is the single matcher for gather/fill/count, so the silk-vs-fortune (ByEnchants) rule is enforced everywhere.
     */
    private boolean matchesSlot(@Nullable ItemStack cand, KitSlot slot) {
        if (cand == Container.EMPTY_STACK || cand == null) return false;
        if (cand.getId() != slot.itemId()) return false;
        SlotMatch eff = slot.match() == null ? SlotMatch.Auto : slot.match();
        return switch (eff) {
            case ByType -> true;
            case ByEnchants -> hasAllEnchants(cand, slot.reqEnch());
            case Exact -> slot.sample() == null || Objects.equals(cand.getDataComponents(), slot.sample().getDataComponents());
            case Auto -> slot.sample() == null || matchesGlobal(cand, slot.sample());  // config Auto = type-only; physical = global mode
        };
    }

    /** True iff the candidate carries every required enchant id (in ENCHANTMENTS or STORED_ENCHANTMENTS; level ignored). */
    private boolean hasAllEnchants(ItemStack cand, List<Integer> req) {
        if (req == null || req.isEmpty()) return true;   // ByEnchants with no enchants == type match
        Map<Integer, Integer> have = new HashMap<>();
        putEnch(have, cand, DataComponentTypes.ENCHANTMENTS);
        putEnch(have, cand, DataComponentTypes.STORED_ENCHANTMENTS);
        for (int id : req) if (!have.containsKey(id)) return false;
        return true;
    }

    private void putEnch(Map<Integer, Integer> into, ItemStack s, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments e = s.getDataComponentsOrEmpty().get(type);
        if (e != null) into.putAll(e.getEnchantments());
    }

    /** The legacy global-mode match of a candidate against a captured physical sample. */
    private boolean matchesGlobal(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == Container.EMPTY_STACK || b == Container.EMPTY_STACK || a == null || b == null) return false;
        if (a.getId() != b.getId()) return false;
        return switch (CONFIG.client.extra.kitMaker.matchMode) {
            case Loose -> true;
            case Exact -> Objects.equals(a.getDataComponents(), b.getDataComponents());
            case Smart -> enchMatch(a, b, DataComponentTypes.ENCHANTMENTS)
                && enchMatch(a, b, DataComponentTypes.STORED_ENCHANTMENTS)
                && Objects.equals(a.getDataComponentsOrEmpty().get(DataComponentTypes.POTION_CONTENTS),
                                  b.getDataComponentsOrEmpty().get(DataComponentTypes.POTION_CONTENTS));
        };
    }

    /** Smart-match an enchantment component: same enchant TYPES (and levels, unless ignoreEnchantLevels). */
    private boolean enchMatch(ItemStack a, ItemStack b, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments ea = a.getDataComponentsOrEmpty().get(type);
        ItemEnchantments eb = b.getDataComponentsOrEmpty().get(type);
        Map<Integer, Integer> ma = ea == null ? Map.of() : ea.getEnchantments();
        Map<Integer, Integer> mb = eb == null ? Map.of() : eb.getEnchantments();
        return CONFIG.client.extra.kitMaker.ignoreEnchantLevels ? ma.keySet().equals(mb.keySet()) : ma.equals(mb);
    }

    private String itemNameOf(KitSlot slot) {
        ItemData d = ItemRegistry.REGISTRY.get(slot.itemId());
        return d != null ? d.name() : "item";
    }

    private List<String> enchNames(KitSlot slot) {
        List<String> out = new ArrayList<>();
        for (int id : slot.reqEnch()) { var e = EnchantmentRegistry.REGISTRY.get(id); out.add(e != null ? e.name() : ("ench#" + id)); }
        return out;
    }

    // ---------------------------------------------------------------- status

    public String statusLine() {
        if (complete) return "done (" + cyclesDone + " kits)";
        if (paused) return "paused";
        if (state == State.IDLE) return "idle";
        return state.name().toLowerCase() + " (" + cyclesDone + " kits)";
    }

    public String setupLine() {
        return "template " + template.size() + " slots, shulkerSrc " + (shulkerSrc != null)
            + ", itemSrcs " + itemSrcs.size() + ", deposit " + (depositChest != null);
    }

    /** Live status for the ABM control plane (kit grid + Setup cards + Status panel + the green estimate badge). */
    public Map<String, Object> statusJson() {
        var cfg = CONFIG.client.extra.kitMaker;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", statusLine());
        m.put("state", state.name());
        m.put("running", state != State.IDLE && !complete && !paused);
        m.put("paused", paused);
        m.put("complete", complete);
        m.put("hazardPaused", hazardPaused);
        m.put("kits", cyclesDone);
        m.put("maxKits", cfg.maxKits);
        m.put("templateSlots", template.size());
        m.put("templateConfigured", !cfg.template.isEmpty());
        m.put("shulkerSrcOk", shulkerSrc != null);
        m.put("depositOk", depositChest != null);
        m.put("itemSrcs", itemSrcs.size());
        m.put("kitsBuildable", kitsBuildable);
        m.put("nextKitFillablePct", (int) Math.round(nextKitFillable * 100));
        m.put("partialMinPct", (int) Math.round(cfg.partialMinSlotFraction * 100));
        m.put("shortfalls", shortfalls);
        m.put("lastError", lastError);
        m.put("lastErrorKind", lastErrorKind);
        m.put("activeKit", cfg.activeKit == null ? "" : cfg.activeKit);
        m.put("kitsSaved", cfg.kits.size());
        m.put("hasCaptured", !cfg.captured.isEmpty());
        return m;
    }

    public boolean isPaused() { return paused; }
    public boolean isComplete() { return complete; }
}
