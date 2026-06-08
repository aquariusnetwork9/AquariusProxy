# AquariusProxy

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.21.4-brightgreen.svg" alt="Minecraft"/>
  <img src="https://img.shields.io/badge/version-2.1.4-blue.svg" alt="Version"/>
  <img src="https://img.shields.io/badge/license-AGPL--3.0-orange.svg" alt="License"/>
</p>

**AquariusProxy** is a headless Minecraft proxy/bot for [2b2t.org](https://www.2b2t.org/) (works on any server), built as a fork of [ZenithProxy](https://github.com/rfresh2/ZenithProxy) by rfresh2.

It keeps everything ZenithProxy does — an always-online account you can also log into and control in-game, driven remotely from Discord, a terminal, or in-game chat — and adds a fix to the inventory engine plus **four built-in automation modules** baked directly into the proxy (no plugin jars to manage): an AFK quarry miner, a packet sniffer, a stasis-pearl loader, and a villager trader.

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

All four modules are **native built-ins**: no plugin jars, no separate config files. Every setting lives in the main `config.json` under `client.extra.<module>` and is configured entirely through commands.

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
