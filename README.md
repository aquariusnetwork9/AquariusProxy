# AquariusProxy

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.21.4-brightgreen.svg" alt="Minecraft"/>
  <img src="https://img.shields.io/badge/version-2.4.0-blue.svg" alt="Version"/>
  <img src="https://img.shields.io/badge/license-AGPL--3.0-orange.svg" alt="License"/>
</p>

**AquariusProxy** is a headless Minecraft proxy/bot for [2b2t.org](https://www.2b2t.org/) (works on any server), built as a fork of [ZenithProxy](https://github.com/rfresh2/ZenithProxy) by rfresh2.

It keeps everything ZenithProxy does — an always-online account you can also log into and control in-game, driven remotely from Discord, a terminal, or in-game chat — and adds a fix to the inventory engine plus **built-in automation modules** baked directly into the proxy (no plugin jars to manage) — including an AFK quarry miner, a packet sniffer, a stasis-pearl loader, a villager trader, and an **anvil auto-enchanter**.

> AquariusProxy implements only the Minecraft network protocol (no rendering/lighting/world-gen). It reads, modifies, cancels, and injects packets in either direction. See the original [ZenithProxy](https://github.com/rfresh2/ZenithProxy) project for the proxy fundamentals and the [2b2t.vc wiki](https://wiki.2b2t.vc) for the full stock command reference.

---

## Table of contents

- [What's different from ZenithProxy](#whats-different-from-zenithproxy)
- [Install & run](#install--run)
- [The ShiftClick / inventory fix](#the-shiftclick--inventory-fix)
- [Built-in modules](#built-in-modules)
  - [AquariusMiner — AFK quarry](#aquariusminer--afk-quarry)
  - [AquariusSniffer — packet sniffer](#aquariussniffer--packet-sniffer)
  - [PearlPlus — stasis pearl loader](#pearlplus--stasis-pearl-loader)
  - [VillagerTrader — automatic trading](#villagertrader--automatic-trading)
  - [PearlDrop — stasis pearl filler](#pearldrop--stasis-pearl-filler)
  - [KitMaker — kit shulker filler](#kitmaker--kit-shulker-filler)
  - [Regear — resupply from a kit shulker](#regear--resupply-from-a-kit-shulker)
  - [ElytraPilot — autopilot elytra flight](#elytrapilot--autopilot-elytra-flight)
  - [Enchanter — anvil auto-enchanting](#enchanter--anvil-auto-enchanting)
- [Command reference](#command-reference)
- [Credits & license](#credits--license)

---

## What's different from ZenithProxy

AquariusProxy is ZenithProxy with the following changes:

| Area | Change |
| --- | --- |
| **Package / branding** | Renamed `com.zenith` → `com.aquarius`; the product identifies as **AquariusProxy** (banner, jar, Discord). |
| **Inventory engine** | Fixed `ShiftClick` quick-move so shift-clicking actually moves items on 2b2t / Grim-protected servers — see [below](#the-shiftclick--inventory-fix). Every automation module that touches a container depends on this. |
| **+ Module** | **AquariusMiner** — AFK single-block quarry with ender-chest storage, auto-restock, tool/food management, anti-stall self-healing. |
| **+ Module** | **AquariusSniffer** — live/buffered packet capture with scenario templates + substring filters. |
| **+ Module** | **PearlPlus** — stasis-pearl loader (whisper-to-load), baked in from the PearlPlus 2.0.9 plugin. |
| **+ Module** | **VillagerTrader** — fully automatic villager buy/sell/restock/store, baked in from the ZenithProxyVillagerTrader 2.0.3 plugin. |
| **+ Module** | **PearlDrop** — throws ender pearls **into** stasis chambers to stock them (the deposit counterpart to PearlPlus). |
| **+ Module** | **KitMaker** — mass-produces filled kit shulkers from a template shulker + a floor of supply chests. |
| **+ Module** | **Regear** — one-shot resupply: pulls a named kit shulker from an ender chest, empties it, and gears up. |
| **+ Module** | **ElytraPilot** — autopilot elytra flight (climb/glide travel, 2b2t nether-highway bounce, overworld↔nether trip planner). |
| **+ Module** | **Enchanter** — auto-builds max-template gear in an anvil station: pulls gear, applies a built-in max template via the **cheapest anvil combine order**, funds the XP from a bottle chest, deposits the result. |

These modules are **native built-ins**: no plugin jars, no separate config files. Every setting lives in the main `config.json` under `client.extra.<module>` and is configured entirely through commands.

Everything from upstream ZenithProxy (AntiAFK, AutoEat, KillAura, AutoTotem, AutoArmor, VisualRange, Spammer, AutoFish, the Discord/terminal/in-game control surfaces, ViaVersion multi-version support, etc.) is still present and unchanged.

---

## Install & run

AquariusProxy runs on Java 21+. There are two ways to run it:

**A. Self-built JAR (recommended for this fork)**

```bash
git clone https://github.com/aquariusnetwork9/AquariusProxy.git
cd AquariusProxy
./gradlew shadowJar          # Windows: gradlew.bat shadowJar
java -jar build/libs/AquariusProxy.jar
```

Or download `AquariusProxy.jar` from the [Releases](https://github.com/aquariusnetwork9/AquariusProxy/releases) page and `java -jar AquariusProxy.jar`.

On first start it generates `config.json` and `launch_config.json` in the working directory and opens a server on `localhost:25565`. Connect with a Minecraft 1.21.4 client (any version via ViaVersion), or drive it from the terminal / Discord. Use the `connect` command to log the bot into the destination server.

> **Note on the launcher:** the official ZenithProxy launcher (auto-update, service management) expects a file named `ZenithProxy.jar`. If you run AquariusProxy under that launcher, deploy the built `AquariusProxy.jar` to its launcher directory **renamed to `ZenithProxy.jar`** (the file contents are what matter — the banner will still read AquariusProxy). Set `auto_update: false` so the launcher keeps your custom jar.

**Control surfaces** (all share the same command set; the prefix differs):

| Surface | How to send a command |
| --- | --- |
| Terminal | type the command directly, **no prefix** (`aquariusminer on`) |
| Discord | configured prefix, default `.` (`.aqm on`) |
| In-game chat | configured prefix (`.aqm on`) |

This README uses the `.` (Discord/in-game) prefix in examples.

---

## The ShiftClick / inventory fix

ZenithProxy's inventory automation moves items with synthetic container clicks. Modern anticheats (2b2t runs **Grim**) validate the `changedSlots` map a client sends with each `ContainerClickPacket` against their own server-side prediction. Stock ZenithProxy's shift-click action sent an **empty** `changedSlots` prediction, so Grim rejected the click and the items never moved — silently breaking any shift-click-based automation (filling shulkers, quick-depositing, bulk trading).

AquariusProxy rewrites [`ShiftClick`](src/main/java/com/aquarius/feature/inventory/actions/ShiftClick.java) to compute the real quick-move outcome, mirroring vanilla `AbstractContainerMenu.quickMoveStack`:

1. Find the quick-move destination region (player inventory ⇄ container).
2. Merge into matching partial stacks first, then fill empty slots.
3. Build an accurate `changedSlots` prediction (destination slots + the reduced/emptied source slot) so the click passes validation.

This is a general correctness fix and the foundation every container-using module here relies on. **AquariusMiner** (shulker fill/store, ender-chest cycle), **VillagerTrader** (restock/trade/store), and **PearlPlus** all break without it.

---

## Built-in modules

### AquariusMiner — AFK quarry

An autonomous single-block quarry. It clears a rectangular area top-down, layer by layer, mines with the right tool, eats, dodges hazards, banks the haul into shulkers via an ender chest (or chests), and self-heals the stalls that normally kill AFK miners on a laggy server.

**What it does**
- **Top-down, layer-major clearing** of a finite area (or an unlimited outward spiral), within a configurable Y band.
- **Ender-chest buffer storage:** when inventory fills it places an ender chest, pulls an empty shulker, shift-click-fills it, stores the full shulker, then silk-touch-recovers the ender chest. Filled shulkers are **never** carried in the mining inventory. (Optionally deposits to physical chests instead.)
- **Tool handling:** auto-selects the best **non-silk** pickaxe/shovel to break with, while keeping a **silk-touch pick reserved in the hotbar** purely for ender-chest recovery. Optional gravel-shovel handling.
- **Hotbar model:** hotbar holds tools + ender chest + food; mined keep-blocks get swept to main inventory; junk (mob drops, non-keep items) is dropped as aggressively as the anticheat-safe action rate allows. Equipped armor and the offhand totem are never dropped.
- **Survival:** pauses for AutoEat, optional food restock from a food shulker in the ender chest, optional pause when a player is near, optional auto-disconnect on completion.
- **Anti-stall self-healing:** reach floor + manual-break assist for fence/edge-block stalls, verify-and-retry of uncleared sub-boxes, and a tool tug-of-war fix that proactively defers tool selection to Meteor's AutoTool when present.

**Setup guide**

1. Put the bot at the start corner with a stack of ender chests, a **silk-touch pickaxe**, an efficiency (non-silk) pickaxe, optionally a shovel and food, and **empty shulker boxes** in the ender chest (it pulls these to bank into).
2. Set the vertical band: `.aqm minY -59` and `.aqm maxY -50` (defaults sit in the deepslate layer).
3. Choose what to keep: `.aqm keep add cobbled_deepslate` (everything not in the keep-list is treated as junk and dropped). `.aqm keep list` to review.
4. Define the area, either:
   - `.aqm here <length> <width>` — a box `length` blocks forward and `width` blocks right of the bot's position+facing, **or**
   - `.aqm area corners <x1> <z1> <x2> <z2>` — explicit corners, **or**
   - `.aqm area chunks <width> <length>` + `.aqm area anchor center|corner`, **or**
   - `.aqm area unlimited` — infinite outward spiral.
5. (Optional) `.aqm reach 2.5` keeps the bot tight to what it mines so drops land next to it; `.aqm sprint on` speeds up repositioning.
6. Start: `.aqm on`. Watch progress with `.aqm scan`. Stop with `.aqm off`.

See the [command reference](#aquariusminer-aqm) for every knob (cave handling, legit line-of-sight mode, full-stacks vs free-slot storage margin, dry-run diagnostics, deposit-to-chests, collect/verify timing, etc.).

---

### AquariusSniffer — packet sniffer

A live/buffered packet inspector for debugging behaviour against the destination server. It's a **separate toggle from the miner** so you can capture packets any time. Enabling it registers an observe-only codec at the head of the client (bot ⇄ server) pipeline — it never alters packets — and disabling it fully unregisters the codec, so there is **zero per-packet overhead when off**. Captured lines go into a rolling buffer (most-recent-N) that survives enable/disable cycles, so `dump` still works after you turn it off.

All sniffer commands live under `.aqm sniff …`.

**Capture controls**

| Command | What it does |
| --- | --- |
| `.aqm sniff on` / `off` | Start / stop capturing into the rolling buffer. |
| `.aqm sniff dump` | Print the buffered packet lines to the console/terminal log. |
| `.aqm sniff clear` | Empty the buffer. |
| `.aqm sniff 1s` / `3s` / `5s` / `10s` | One-shot **verbose** capture (forces live + body on) for N seconds, then auto-dumps and turns the sniffer off. The fastest way to grab a short trace. |

**Output controls**

| Command | What it does |
| --- | --- |
| `.aqm sniff live on` / `off` | Mirror each matching packet to the log in real time (in addition to buffering). |
| `.aqm sniff body on` / `off` | `on` = log the full packet `toString()` (all fields); `off` = log just the packet class name. |
| `.aqm sniff dir in` / `out` / `both` | Restrict to inbound (from server), outbound (bot → server), or both. |

**Filtering** — two independent filters are AND-ed together; a packet is captured only if it passes **both**:

1. **Template** — a named scenario that matches a curated set of packet-name substrings.

   | Command | What it does |
   | --- | --- |
   | `.aqm sniff template list` | List the available templates. |
   | `.aqm sniff template <name>` | Apply a template (forgiving match: `block` → `blocks`, `chunk` → `chunks`, `entit` → `entities`). |
   | `.aqm sniff template off` | Clear the template (capture all packet types). |

   Built-in templates and the substrings they match (case-insensitive, against the packet class simple name):

   | Template | Matches packet names containing |
   | --- | --- |
   | `movement` | playerposition, moveplayer, moveentity, teleport, rotate, velocity, vehiclemove, setentitymotion |
   | `combat` | damage, hurt, interact, sethealth, entityevent, removeentities, attack |
   | `inventory` | container, setslot, setcontent, openscreen, carrieditem, creativemodeslot, setheldslot |
   | `blocks` | blockupdate, blockdestruction, playeraction, useitem, blockchangedack, blockevent, sectionblocks |
   | `chunks` | levelchunk, forgetlevelchunk, chunkcachecenter, chunkcacheradius, lightupdate |
   | `chat` | chat, systemchat, disguised, playerinfo |
   | `entities` | addentity, removeentities, setentitydata, moveentity, teleportentity, entityevent |
   | `keepalive` | keepalive, ping, pong |

2. **Substring filter** — a free-text substring matched against the packet class name, for narrowing within (or instead of) a template.

   | Command | What it does |
   | --- | --- |
   | `.aqm sniff filter <text>` | Only capture packets whose class name contains `<text>` (e.g. `filter merchant`, `filter setslot`). |
   | `.aqm sniff filter off` | Clear the substring filter. |

**Examples**

```
.aqm sniff template inventory      # only inventory/container packets
.aqm sniff dir in                  # ...inbound only
.aqm sniff filter merchant         # ...and only those whose name contains "merchant"
.aqm sniff 5s                      # capture 5s verbose, auto-dump, auto-off

.aqm sniff template movement       # debug pathing/teleport behaviour
.aqm sniff body on                 # see full packet fields
.aqm sniff on                      # ...then dump/clear as needed
```

Buffer size, live, body, dir, filter, and template all persist in `config.json` under `client.extra.aquariusMiner` (`sniff*` fields).

---

### PearlPlus — stasis pearl loader

A stasis-pearl loader baked in from the [PearlPlus 2.0.9](https://github.com/duccss/PearlPlus) plugin (by duccss / steve2b2t). Players whisper the bot to teleport themselves home via a stored ender-pearl trapped in a stasis chamber. Coexists with ZenithProxy's separate `PearlLoader` — no collision.

**What it does**
- Per-player stored pearls (a player can have several, each with an id and a trigger location), an optional default pearl id, and a whitelist.
- Players whisper `load` (default pearl) or `load <id>` and the bot pulls that player's pearl.
- Auto-detect of newly placed pearls (with an optional temporary mode + distance check), optional return-to-start-position after firing, and optional drop-pearl-after-load.

**Setup guide**

1. Build your stasis chambers and note each trigger block/pearl location.
2. Register a pearl for a player: `.pp add <playerName> <pearlId> <x> <y> <z>`.
3. (Optional) set a default so a bare `load` works: `.pp defaultpearlid <id>`.
4. (Optional) lock it down: `.pp whitelist on` then `.pp whitelist add <playerName>`.
5. Enable it: `.pp on`. Players now whisper the bot `load` / `load <id>`.

See the [command reference](#pearlplus-pp).

---

### VillagerTrader — automatic trading

Fully automatic villager trading baked in from the [ZenithProxyVillagerTrader 2.0.3](https://github.com/rfresh2/ZenithProxyVillagerTrader) plugin (by rfresh2). It runs your configured trades one at a time, continuously.

**What it does** — per trade, the loop:
1. **Restocks** trade inputs from a chest when they run low (crafts emerald blocks into emeralds as needed).
2. **Trades** with the nearest villager of the configured profession (shift-click bulk trading, or one-at-a-time for enchanted books to avoid buying the wrong book).
3. **Stores** the bought output into a chest when it accumulates.
4. Optional **post-trade storage** (return leftovers to restock or an overflow chest).

Supports one- or two-input trades, per-trade price caps, restock thresholds/amounts, enchantment filters (only buy a book if it has the desired enchant at the desired level), and Discord trade-completion notifications.

**Setup guide**

1. Build a **compact** villager trading hall (the bot only trades villagers within render distance — it won't go searching).
2. Place restock chests (inputs) and store chests (outputs); a hopper feed/drain makes it continuous.
3. Add a trade:
   - One input: `.trader add <id> <profession> <inputItem> <outputItem> <inputChestPos> <outputChestPos>`
   - Two inputs: `.trader add <id> <profession> <input1> <input2> <outputItem> <input1ChestPos> <input2ChestPos> <outputChestPos>`
4. Tune it via `.trader set help` (prices, restock thresholds, enchantment filters, post-trade storage).
5. Start: `.trader on`. Review with `.trader list`.

See the [command reference](#villagertrader-trader).

---

### PearlDrop — stasis pearl filler

The **deposit** counterpart to [PearlPlus](#pearlplus--stasis-pearl-loader): instead of pulling a trapped pearl to teleport a player home, PearlDrop **throws pearls into** stasis chambers to stock them. A chamber is a 1×1 water/bubble column with soul sand at the bottom and a trapdoor on top. The bot walks to the rim, sneak-overhangs the opening, aims at the centre of the soul sand, throws a pearl in, and steps back off the ledge.

**What it does**
- **Scans** loaded chunks for chambers (`.pd scan [radius]`) and lists them with indices + a ready/occupied verdict.
- **Deposits** by coordinate (`.pd drop <x> <y> <z> [n]` — auto-picks an unoccupied chamber near the coords), by scan index (`.pd pick 1 3 4`), or into every empty chamber from the last scan (`.pd all`).
- Configurable pearls-per-chamber, throw spacing, aim eye-height, and minimum water depth. Exposes a service API so other automation (e.g. a stash-mover) can drive deposits.

**Setup guide**
1. Build your stasis chambers (water column at least the minimum depth, soul sand bottom, trapdoor top) and give the bot a supply of ender pearls.
2. Stand near them and `.pd scan` to list them.
3. `.pd all` to fill every empty one, or `.pd pick <indices>` / `.pd drop <x> <y> <z>` for specific chambers.

See the [command reference](#pearldrop-pd).

---

### KitMaker — kit shulker filler

Mass-produces filled "kit" shulkers from a template. Point it at one example kit shulker and it reproduces that exact kit — same items, counts, and slots — over and over, pulling empties and depositing finished kits until it runs out of shulkers or materials.

**What it does**
- Reads a **template** kit shulker from a designated chest (exact item + count per slot, so partial/aesthetic stacks are preserved).
- Auto-discovers the floor-level containers around it within a radius and classifies them: a chest of empty shulkers (the source), an empty chest (the finished-kit deposit), and the rest as item sources.
- Loops: pull an empty shulker → gather the kit's items → place + fill the shulker to match the template → break + collect it → deposit it.
- Match strictness is configurable (Loose = item type only, Smart = ignore cosmetic name/lore/durability, Exact = identical components). Block-breaking is forbidden except during the shulker-harvest step, so it never digs the floor while pathing between chests.

**Setup guide**
1. Build a floor of chests within the scan radius: one holding the example **template** shulker, one of **empty shulkers**, an **empty** chest for finished kits, and chests holding the kit's **materials**.
2. Set the template chest: `.km template <x> <y> <z>`.
3. Stand on the floor, set the radius (`.km scanradius <n>`), then `.km on`. Review with `.km status`.

See the [command reference](#kitmaker-km).

---

### Regear — resupply from a kit shulker

A one-shot resupply: place an ender chest, pull a named **kit shulker** out of it, empty the kit into your inventory, return the empty shulker, optionally equip the armor and offhand a totem — then turn itself off. For topping a combat/travel bot back up to a full loadout from an ender-chest-stored kit.

**What it does**
- Places + opens an ender chest (or finds a placed one within a fallback radius), pulls the kit shulker matched **by custom name** or **by colour**, empties it, returns the empty shulker, and gears up.
- Optional: equip the kit's armor, offhand a totem, return the emptied shulker, pause when a player is near, and disable-when-done (default on — it's a one-shot).

**Setup guide**
1. Stock an ender chest with a kit shulker (a unique custom name or colour) containing your loadout, plus ender chests to place.
2. Tell Regear how to find it: `.rg name <text>` or `.rg color <colour>`.
3. `.rg armor on` / `.rg totem on` if it should equip + offhand, then `.rg on` to run it once.

See the [command reference](#regear-rg).

---

### ElytraPilot — autopilot elytra flight

Autonomous elytra flight: deploys, steers, fires fireworks, avoids terrain, and lands — including 2b2t **nether-highway** travel and a **trip planner** that routes overworld↔nether for long hauls. Needs an elytra worn and firework rockets in the hotbar.

**What it does**
- **Point-to-point** (`.fly to <x> <z>`): flies an efficient **climb/glide** profile — firework-climb to a ceiling, then glide with no fireworks down to a floor, repeat — so most of the trip is free glide. Terrain-aware descent + landing (re-routes around flight-level walls, hands the last covered/indoor leg to Baritone).
- **Bounce-highway** (`.fly ebounce on`, or `.fly highway <dir>`): the no-firework "bounce" technique along a flat road or a 2b2t nether highway from 0,0 (N/S/E/W + diagonals).
- **Trip planner** (`.fly trip <x> <z>`): overworld-direct within ~100k of spawn, otherwise enters the nether, flies the nether leg (open-nether 3D look-ahead pathfinding or a highway e-bounce), and exits near the target.
- **Elytra swap** mid-flight when the worn elytra wears out, a **chunk-loading governor** so it won't outrun 2b2t's slow chunk streaming, and a **2b2t speed cap** (~40 b/s).

> ElytraPilot is the newest and most experimental module. Flight basics (takeoff/cruise/descent/landing) and bounce-highways are validated; the nether trip planner is a first cut. Test over open ground before relying on it for a long haul.

**Setup guide**
1. Wear an elytra and put a stack (or several) of firework rockets in the hotbar; optionally a spare elytra in the inventory with `.fly swap on`.
2. Short hop: `.fly to <x> <z>` over open ground.
3. Long haul: `.fly trip <x> <z> [y]` (handles the nether routing), or `.fly highway <dir>` then `.fly on` to ride a nether highway.

See the [command reference](#elytrapilot-fly).

---

### Enchanter — anvil auto-enchanting

Auto-builds fully-enchanted "max template" gear in a dedicated anvil station. Stock chests with un-enchanted gear and pre-made enchanted books, and the bot turns them into god gear one piece at a time — in the **cheapest possible anvil order**, funding the XP cost itself from a chest of bottles.

**What it does**
- **Pull → enchant → deposit:** pulls a base item from the input chest, applies the built-in **max template** for its type, deposits the finished piece into the output chest, and loops until the input runs out.
- **Cheapest anvil order:** Minecraft's anvil cost is order-dependent — every item carries a "prior work penalty" that doubles each use, and any single combine costing **40+ levels is blocked** ("Too Expensive!"). The Enchanter runs an exact search over all binary combine-trees to find the minimum-XP order that keeps every step under the cap, merging books into intermediates first so the gear's penalty stays low. (A god sword is ~72 levels done optimally vs ~171 naively — which would hit "Too Expensive!".)
- **XP-bottle charging:** the anvil charges *levels*, and high levels cost disproportionately more XP, so the bot tops up a small buffer right before each step instead of banking the whole run up front — using **~2–7× fewer bottles**. It throws bottles from a chest at its own feet and collects the orbs, reading its live XP level to know when it has enough.
- **Gravity-fed anvil pillar:** anvils shatter with use. Stack a **pillar of anvils** over the work spot; when the bottom one breaks the next falls into place — the bot detects the gap, waits for the replacement to settle, and continues.
- **Auto-discovered layout:** finds the anvil + all chests within 32 blocks and classifies them by content — no coordinates to type.

**Built-in templates** (one max loadout per gear family, with mutually-exclusive *variant* picks you choose):

| Family | Always applied | Variant groups (default first) |
| --- | --- | --- |
| sword | unbreaking 3, mending, looting 3, fire_aspect 2, knockback 2, sweeping_edge 3 | `sword_damage`: sharpness \| smite \| bane_of_arthropods |
| pickaxe / shovel / hoe | efficiency 5, unbreaking 3, mending | `tool_yield`: fortune \| silk_touch |
| axe | efficiency 5, unbreaking 3, mending | `axe_yield`: fortune \| silk_touch · `axe_damage`: sharpness \| smite \| bane_of_arthropods |
| helmet | unbreaking 3, mending, respiration 3, aqua_affinity, thorns 3 | `armor_protection`: protection \| blast \| fire \| projectile |
| chestplate | unbreaking 3, mending, thorns 3 | `armor_protection` |
| leggings | unbreaking 3, mending, thorns 3, swift_sneak 3 | `armor_protection` |
| boots | unbreaking 3, mending, thorns 3, feather_falling 4, soul_speed 3 | `armor_protection` · `boots_walk`: depth_strider \| frost_walker |
| bow | power 5, unbreaking 3, flame, punch 2 | `bow_special`: infinity \| mending |
| crossbow | quick_charge 3, unbreaking 3, mending | `crossbow_special`: multishot \| piercing |
| trident | unbreaking 3, mending, impaling 5 | `trident_style`: throw (loyalty 3 + channeling) \| riptide 3 |
| mace | unbreaking 3, mending, wind_burst 3 | `mace_impact`: density \| breach |
| fishing_rod | unbreaking 3, mending, lure 3, luck_of_the_sea 3 | — |
| elytra / shield | unbreaking 3, mending | — |

Run `.enc templates` to print the resolved sets under your current variant picks, plus the estimated **levels and bottles** each one costs.

**Setup guide**
1. Build a **station** within a 32-block radius of where the bot stands:
   - a **pillar of anvils** over the work spot (so a fresh anvil drops when one shatters);
   - a chest of **un-enchanted gear** (the input);
   - one or more chests/barrels of **pre-made single-enchant books** (the book source — double chests are fine);
   - a chest of **XP bottles** (a near-unlimited / hopper-fed supply is ideal);
   - an **empty** chest for the finished gear (the output).
2. (Optional) choose variants: `.enc variant sword_damage smite`, `.enc variant tool_yield silk_touch`, … (`.enc templates` lists every group + option).
3. Stand the bot in the station and `.enc scan` — it reports which roles it found (anvil / input / books / xp-chest / output). Fix anything that reads `false`.
4. `.enc on`. Stop with `.enc off`; watch progress with `.enc status`.

**Notes & limits**
- Books must be **single-enchant** books (one enchantment each — the usual villager/loot book). Pair it with **VillagerTrader** to mass-produce them.
- The input gear must be a known type from the table above; anything else is left in the input chest.
- If XP charging is off (`.enc xp off`), keep the bot's level topped up another way — it pauses if a combine costs more levels than it has.

See the [command reference](#enchanter-enc).

---

## Command reference

> Prefix: none in the terminal, `.` in Discord/in-game (configurable). Aliases shown in parentheses.

### AquariusMiner (`aqm`)

```
.aquariusminer on/off
.aqm minY <y>
.aqm maxY <y>
.aqm here <length> <width>            # box: length forward, width right, from bot pos+facing
.aqm area unlimited
.aqm area chunks <width> <length>
.aqm area anchor <center/corner>
.aqm area corners <x1> <z1> <x2> <z2>
.aqm keep add <item> | remove <item> | list | clear | reset
.aqm cave on/off
.aqm legit on/off                     # break only blocks in line of sight
.aqm reach <blocks>                   # legit reach; lower = closer/better pickup; ~4.5 vanilla
.aqm sprint on/off                    # faster repositioning (toggle off/on to apply)
.aqm fullstacks on/off
.aqm freeslots <n>                    # store margin when full-stacks is off
.aqm dryrun on/off                    # log echest shulkers + abort, no pull/store
.aqm badfood on/off
.aqm autodc on/off                    # auto-disconnect on completion
.aqm pauseplayer on/off               # pause when a player is near
.aqm restock on/off
.aqm shovel on/off
.aqm food on/off | food count <n> | food min <n>
.aqm clearbox <size>
.aqm layer <blocks>
.aqm verify on/off | verify retries <n>
.aqm collect on/off | collect seconds <n>
.aqm scan                             # print the current area scan / progress

# Storage (ender-chest buffer is default; or deposit to physical chests):
.aqm deposit on/off
.aqm deposit chest add <x> <y> <z> | clear
.aqm deposit supply add <x> <y> <z> | clear
.aqm deposit refill on/off | empties <n> | maxdist <blocks>

# Packet sniffer (see the AquariusSniffer section for filters):
.aqm sniff on/off | dump | clear | 1s | 3s | 5s | 10s
.aqm sniff live on/off | body on/off | dir in/out/both
.aqm sniff filter <text>/off | template <name>/list/off
```

### VillagerTrader (`trader`)

```
.trader on/off
.trader add <id> <profession> <inputItem1> <outputItem> <inputItem1ChestPos> <outputChestPos>
.trader add <id> <profession> <inputItem1> <inputItem2> <outputItem> <input1ChestPos> <input2ChestPos> <outputChestPos>
.trader del <id>
.trader clear
.trader list
.trader set help                      # prints all per-trade setting subcommands
.trader waitForInteractTimeout <ticks>
.trader logTradeStatusToDiscord on/off
```

`.trader set help` covers: per-trade `on/off`, `profession`, `inputItem1/2`, `outputItem`, `inputItem1/2Chest`, `outputChest`, `maxInput1/2PerTrade`, `inputItem1/2RestockStacks`, `inputItem1/2RestockCountThreshold`, `outputItemStoreCountThreshold`, `outputEnchants add/del/clear/list`, `postTradeStore <none/to_restock/to_overflow>`, and `overflowChest`.

### PearlPlus (`pp`)

```
.pearlplus on/off
.pp list | list clear
.pp add <playerName> <pearlId> <x> <y> <z>
.pp del <playerName> <pearlId>
.pp defaultpearlid <word|none>
.pp load <playerName> <pearlId>
.pp returnpos on/off
.pp strict on/off
.pp autodetect on/off
.pp autodetect temp on/off
.pp distancecheck on/off
.pp autodefault on/off
.pp whitelist on/off | add | remove | clear | list
.pp droppearlafterload on/off
```

### PearlDrop (`pd`)

```
.pearldrop on/off
.pd scan [radius]                 # find chambers in loaded chunks; lists them with indices
.pd list                          # reprint the last scan
.pd drop <x> <y> <z> [n]          # deposit into the chamber near these coords (auto-picks an empty one)
.pd pick <indices>                # deposit into scanned chambers by index (e.g. pick 1 3 4)
.pd all                           # deposit into every empty chamber from the last scan
.pd count <n>                     # pearls to deposit per chamber
.pd stop                          # cancel the current run
.pd depth <n> | radius <n> | yrange <n> | tolerance <n>    # chamber detection / scan range
.pd spacing <ticks> | eyeheight <d> | priority <n>         # throw timing / aim
.pd closetrapdoor on/off          # shut an open trapdoor before depositing
```

### KitMaker (`km`)

```
.kitmaker on/off
.km template <x> <y> <z>          # chest holding the example kit shulker
.km scanradius <n>                # container discovery radius (default 20)
.km floorband <down> <up>         # accept containers feetY-down .. feetY+up
.km match loose/smart/exact
.km enchantlevels on/off          # smart match: ignore enchant levels
.km maxkits <n>                   # 0 = until shulkers/materials run out
.km pauseplayer on/off
.km autodc on/off                 # disconnect when done
.km status                        # print the discovered layout
```

### Regear (`rg`)

```
.regear on/off
.rg name <text>                   # match the kit shulker by custom name
.rg color <name>/off              # ...or by colour instead
.rg scanradius <n>                # fallback: find a placed echest within n blocks
.rg armor on/off                  # equip the kit's armor on finish
.rg totem on/off                  # offhand a totem on finish
.rg return on/off                 # return the emptied shulker to the echest
.rg pauseplayer on/off
.rg once on/off                   # toggle off after a successful regear
```

### ElytraPilot (`fly`)

```
.elytrapilot on/off
.fly to <x> <z>                   # fly to coords (climb/glide), then land
.fly heading <degrees>            # free-fly a compass bearing until stopped
.fly trip <x> <z> [y]             # plan a journey (overworld, or routed through the nether)
.fly trip highways on/off         # nether leg: e-bounce a highway vs fly open-nether straight
.fly trip off                     # cancel an in-progress trip
.fly highway <N/S/E/W/NE/SE/NW/SW>  # follow a 2b2t nether highway from 0,0
.fly ebounce on/off | road <y> | maxspeed <bps>           # no-firework bounce-highway
.fly ceiling <y> | floor <y> | glidepitch <deg> | climbpitch <deg>   # climb/glide profile
.fly swap on/off | swapdur <n> | sparedur <n>             # mid-flight elytra swap
.fly nethercruise <y> | netherceiling <y> | netherpath on/off | netherfrontier <slow> <hold>
# plus fine-tuning knobs (run `.fly` for the full list): boostspeed, interval, lookahead,
#   arrive, descend, glideratio, groundy, takeoff, reroute, landsearch, baritoneland, ...
```

### Enchanter (`enc`)

```
.enchanter on/off
.enc scan                         # auto-discover the station (anvil + chests) within range
.enc radius <n>                   # discovery radius in blocks (max 32)
.enc band <down> <up>             # Y range to scan, relative to the bot's feet (default 3/3)
.enc variant <group> <choice>     # pick a template variant, e.g. sword_damage smite
.enc templates                    # print resolved templates + estimated levels/bottles
.enc max <n>                      # stop after n items (0 = until the input chest is empty)
.enc xp on/off                    # throw XP bottles from the bottle chest to fund the anvil
.enc xpbuffer <levels>            # top up to (step cost + this) before each step
.enc xpreserve <n>                # bottles to keep carried
.enc pace <action> <settle> <fill> <anvil>   # tick delays (raise on a laggy server)
.enc pauseplayer on/off           # pause when a player is near
.enc autodc on/off                # auto-disconnect when the input is empty
.enc status                       # print the discovered layout + progress
```

Variant groups: `sword_damage` (sharpness/smite/bane_of_arthropods), `tool_yield` & `axe_yield` (fortune/silk_touch), `axe_damage` (sharpness/smite/bane_of_arthropods), `armor_protection` (protection/blast_protection/fire_protection/projectile_protection), `boots_walk` (depth_strider/frost_walker), `bow_special` (infinity/mending), `crossbow_special` (multishot/piercing), `trident_style` (throw/riptide), `mace_impact` (density/breach).

### Stock ZenithProxy commands

All upstream commands remain available (`connect`, `disconnect`, `help`, `antiafk`, `autoeat`, `killaura`, `autototem`, `autoarmor`, `visualrange`, `spammer`, `autofish`, `playtime`, `stats`, `waypoints`, …). Run `help` for the full list, or see the [2b2t.vc command wiki](https://wiki.2b2t.vc/Commands).

---

## Development

Java 21+ to run; Gradle installs the toolchain it needs to compile. Useful tasks:

- `./gradlew run` — build and run a local dev instance
- `./gradlew shadowJar` — build the executable jar to `build/libs/AquariusProxy.jar`
- `./gradlew nativeCompile` — build a GraalVM native image to `build/native/nativeCompile/AquariusProxy` (requires a GraalVM JDK)

---

## Credits & license

- **Original project:** [ZenithProxy](https://github.com/rfresh2/ZenithProxy) by **rfresh2** — AquariusProxy is a fork and would not exist without it.
- **VillagerTrader** module: ported from [ZenithProxyVillagerTrader](https://github.com/rfresh2/ZenithProxyVillagerTrader) by **rfresh2**.
- **PearlPlus** module: ported from [PearlPlus](https://github.com/duccss/PearlPlus) by **duccss / steve2b2t**.

AquariusProxy is licensed under the [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.en.html), the same license as ZenithProxy. The original copyright and license are retained — see [LICENSE](LICENSE). As an AGPL work, the full source is available in this repository.

### Special thanks (inherited from ZenithProxy)

[odpay](https://github.com/odpay/) ·
[DaPorkchop_'s Pork2b2tBot](https://github.com/PorkStudios/Pork2b2tBot) ·
[MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) ·
[Baritone](https://github.com/cabaletta/Baritone) ·
[Netty](https://github.com/netty/netty) ·
[GraalVM](https://graalvm.org/) ·
[ViaVersion](https://github.com/ViaVersion/ViaVersion) ·
[RaphiMC's MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth) ·
[JDA](https://github.com/DV8FromTheWorld/JDA) ·
[JLine](https://github.com/jline/jline3) ·
[Adventure](https://github.com/PaperMC/adventure) ·
and many more open-source libraries.
