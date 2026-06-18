# AquariusProxy

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.21.4-brightgreen.svg" alt="Minecraft"/>
  <img src="https://img.shields.io/badge/version-5.0.0-blue.svg" alt="Version"/>
  <img src="https://img.shields.io/badge/release-pre--release%20%2F%20beta-yellow.svg" alt="Pre-release / beta"/>
  <img src="https://img.shields.io/badge/license-AGPL--3.0-orange.svg" alt="License"/>
</p>

**AquariusProxy** is a headless Minecraft proxy/bot for [2b2t.org](https://www.2b2t.org/) (works on any server), built as a fork of [ZenithProxy](https://github.com/rfresh2/ZenithProxy) by rfresh2.

It keeps everything ZenithProxy does — an always-online account you can also log into and control in-game, driven remotely from Discord, a terminal, or in-game chat — and adds a fix to the inventory engine plus a set of **native automation modules** baked directly into the proxy (no plugin jars to manage): an AFK quarry miner, a packet sniffer, a stasis-pearl loader and filler, a villager trader, a kit-shulker filler, a one-shot regear, an autopilot elytra flyer, an anvil auto-enchanter, and — new in v3.0.0 — a **Postgres-backed stash scanner + payment-gated order picker**.

> 📖 **Setup guides, configuration, and the full command reference live on the [AquariusProxy Wiki](https://github.com/aquariusnetwork9/AquariusProxy/wiki).** This README is just an overview of what's here.

> AquariusProxy implements only the Minecraft network protocol (no rendering / lighting / world-gen). It reads, modifies, cancels, and injects packets in either direction. See the original [ZenithProxy](https://github.com/rfresh2/ZenithProxy) project for the proxy fundamentals and the [2b2t.vc wiki](https://wiki.2b2t.vc) for the full **stock** command reference.

---

## Contents

- [What's new in 5.0 (beta)](#whats-new-in-50-beta)
- [What's different from ZenithProxy](#whats-different-from-zenithproxy)
- [Quick start](#quick-start)
- [Built-in modules](#built-in-modules)
- [Credits & license](#credits--license)

---

## What's new in 5.0 (beta)

> ⚠️ **5.0 is a pre-release (beta).** The jars are built by GitHub Actions with a signed [build-provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations) and published on the Aquarius Launcher's `java.1.21.4` channel, but 5.0 has **not been promoted to a stable tag** yet. Expect rough edges on the items flagged ⚠️ below. Full notes live on the wiki: **[What's New in 5.0 →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/Whats-New)**.

The 5.0 line rolls up everything since the 4.0.0 stable plus upstream stability fixes:

| Feature | Status |
| --- | --- |
| **Boat autopilot** — a server-side port of Minecraft's `AbstractBoat` physics; `.boat goto <x> <z>` drives a boat across open water to a coordinate. | ✅ live-validated on 2b2t |
| **Bubble-column traversal** — the pathfinder rides soul-sand columns up / magma columns down as first-class moves. | ✅ live-validated *(exit-timing polish pending)* |
| **KitMaker** — mass-produces filled kit shulkers from a template + supply chests, with partial-kit support. | ✅ live-validated on 2b2t |
| **Backported ZenithProxy stability fixes** — disconnect-deadlock re-dispatch, cookie-request 2b2t kick, MCProfile username/UUID lookup replacing the dead Minetools API. | ✅ |
| **GitHub-built pre-releases** — the `+1.21.4.pre` source jar and the `+java.1.21.4` launcher-channel jar are now built and published by CI, with provenance attestation. | ✅ |
| **PearlDrop** — throws ender pearls *into* stasis chambers to stock them (the deposit counterpart to PearlPlus). | ⚠️ beta — built & deployed, not yet fully run live |
| **Enchanter** (`/enc`) — cheapest-anvil-order auto-enchanting of god gear. | ⚠️ beta — built & deployed, not yet fully run live |
| **ElytraPilot multi-leg long-haul** — chaining several flight / highway / portal legs to a far destination. | ⚠️ beta *(single-leg flight + the no-firework highway bounce are validated)* |

---

## What's different from ZenithProxy

AquariusProxy is ZenithProxy with the following changes:

| Area | Change |
| --- | --- |
| **Package / branding** | Renamed `com.zenith` → `com.aquarius`; the product identifies as **AquariusProxy** (banner, jar, Discord). |
| **Inventory engine** | Fixed `ShiftClick` quick-move so shift-clicking actually moves items on 2b2t / Grim-protected servers. Every container-using module depends on it — see [The ShiftClick fix](https://github.com/aquariusnetwork9/AquariusProxy/wiki/The-ShiftClick-Fix). |
| **+ Module** | **AquariusMiner** — AFK single-block quarry with ender-chest storage, auto-restock, tool/food management, anti-stall self-healing. |
| **+ Module** | **AquariusSniffer** — live/buffered packet capture with scenario templates + substring filters. |
| **+ Module** | **PearlPlus** — stasis-pearl loader (whisper-to-load), baked in from the PearlPlus 2.0.9 plugin. |
| **+ Module** | **VillagerTrader** — fully automatic villager buy/sell/restock/store, baked in from the ZenithProxyVillagerTrader 2.0.3 plugin. |
| **+ Module** | **PearlDrop** — throws ender pearls **into** stasis chambers to stock them (the deposit counterpart to PearlPlus). |
| **+ Module** | **KitMaker** — mass-produces filled kit shulkers from a template shulker + a floor of supply chests. |
| **+ Module** | **Regear** — one-shot resupply: pulls a named kit shulker from an ender chest, empties it, and gears up. |
| **+ Module** | **ElytraPilot** — autopilot elytra flight (climb/glide travel, 2b2t nether-highway bounce, overworld↔nether trip planner). |
| **+ Module** | **Enchanter** — auto-builds max-template gear in an anvil station via the **cheapest anvil combine order**. |
| **+ Stash system** | **StashScanner** + **OrderFiller** + native **Order System** — a Postgres/Redis-backed stash census, payment-gated order picker, and Discord order intake/manifest. Reuses the proxy's existing database + Discord layers. See the [Order System wiki](https://github.com/aquariusnetwork9/AquariusProxy/wiki/OrderSystem). |

These modules are **native built-ins**: no plugin jars, no separate config files. They all default to **off** and add no overhead until enabled. Every setting lives in the main `config.json` under `client.extra.<module>` and is configured entirely through commands ([full reference on the wiki](https://github.com/aquariusnetwork9/AquariusProxy/wiki/Command-Reference)).

Everything from upstream ZenithProxy (AntiAFK, AutoEat, KillAura, AutoTotem, AutoArmor, VisualRange, Spammer, AutoFish, the Discord/terminal/in-game control surfaces, ViaVersion multi-version support, etc.) is still present and unchanged.

---

## Quick start

> ⚠️ **Java only — supported platforms & specs.** AquariusProxy is validated **only on the Java release channel** (the `.jar`, run on **Java 21+**) on **Windows, Ubuntu/Linux, and macOS**. The native GraalVM (`linux`) build and any other OS or runtime are **not tested or validated** — use the Java jar. The JVM + ViaVersion need roughly **600 MB on top of the OS**, so run it on a host with **at least 1 GB of RAM (2 GB recommended)**. A 512 MB VPS is **not** enough.

AquariusProxy runs on **Java 21+**. Build it:

```bash
git clone https://github.com/aquariusnetwork9/AquariusProxy.git
cd AquariusProxy
./gradlew shadowJar          # Windows: gradlew.bat shadowJar
java -jar build/libs/AquariusProxy.jar
```

Or download `AquariusProxy.jar` from the [Releases](https://github.com/aquariusnetwork9/AquariusProxy/releases) page and `java -jar AquariusProxy.jar`. On first start it opens a server on `localhost:25565`; use the `connect` command to log the bot into the destination server.

Release jars are published with a signed [build-provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations). Verify a downloaded jar against this repo with the [GitHub CLI](https://cli.github.com/):

```bash
gh attestation verify AquariusProxy.jar --repo aquariusnetwork9/AquariusProxy
```

➡️ **Full install, hosting, the ZenithProxy launcher, and the control surfaces (terminal / Discord / in-game) are on the [Installation wiki page](https://github.com/aquariusnetwork9/AquariusProxy/wiki/Installation).**

---

## Built-in modules

Each module below links to its wiki page for the setup guide and commands.

> **Validation status (as of the v5.0 beta line):** every feature is live-validated on 2b2t **except** the three marked ⚠️ — **PearlDrop**, the **Enchanter**, and ElytraPilot's **multi-leg long-haul** trip planner — which are built and deployed but not yet fully run live.

### AquariusMiner — AFK quarry

An autonomous single-block quarry. It clears a rectangular area top-down, layer by layer, mines with the right tool, eats, dodges hazards, banks the haul into shulkers via an ender chest, and self-heals the stalls that normally kill AFK miners on a laggy server.

- Top-down, layer-major clearing of a finite area or an unlimited outward spiral, within a configurable Y band.
- Ender-chest **buffer storage**: places an ender chest, fills/stores shulkers, and silk-touch-recovers the chest; filled shulkers are never carried while mining. (Optionally deposits to physical chests instead.)
- Tool handling that breaks with the best non-silk tool while reserving a silk pick for chest recovery; food restock; junk dropping at an anticheat-safe rate.
- Anti-stall self-healing: reach floor + manual-break assist, verify-and-retry of uncleared sub-boxes, and a tool tug-of-war fix.

📖 [AquariusMiner wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/AquariusMiner)

### AquariusSniffer — packet sniffer

A live/buffered packet inspector for debugging behaviour against the destination server, a **separate toggle** from the miner.

- Observe-only — registers a codec at the head of the pipeline that never alters packets, with **zero per-packet overhead when off**.
- Rolling buffer that survives enable/disable (so `dump` works after you turn it off), plus one-shot timed verbose captures.
- Filter captures by **scenario template** (movement, combat, inventory, blocks, chunks, chat, entities, keepalive) and/or a free-text substring, AND-ed together.

📖 [AquariusSniffer wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/AquariusSniffer)

### PearlPlus — stasis pearl loader

A stasis-pearl loader baked in from the [PearlPlus 2.0.9](https://github.com/duccss/PearlPlus) plugin (by duccss / steve2b2t). Players whisper the bot to teleport themselves home via a stored ender pearl trapped in a stasis chamber. Coexists with ZenithProxy's separate `PearlLoader`.

- Per-player stored pearls (several each, by id + trigger location), an optional default, and a whitelist.
- Whisper `load` / `load <id>` to fire; auto-detect of newly placed pearls, optional return-to-start and drop-after-load.

📖 [PearlPlus wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/PearlPlus)

### VillagerTrader — automatic trading

Fully automatic villager trading baked in from the [ZenithProxyVillagerTrader 2.0.3](https://github.com/rfresh2/ZenithProxyVillagerTrader) plugin (by rfresh2). It runs your configured trades one at a time, continuously: **restock** inputs from a chest (crafting emerald blocks as needed) → **trade** the nearest matching villager → **store** the output.

- One- or two-input trades, per-trade price caps, restock thresholds/amounts.
- Enchantment filters (only buy a book with the desired enchant at the desired level) and Discord trade-completion notifications.
- The natural way to mass-produce the single-enchant books the [Enchanter](#enchanter--anvil-auto-enchanting) consumes.

📖 [VillagerTrader wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/VillagerTrader)

### PearlDrop — stasis pearl filler *(⚠️ not yet validated live)*

The **deposit** counterpart to PearlPlus: instead of pulling a trapped pearl, PearlDrop **throws pearls into** stasis chambers to stock them. It walks to the rim, sneak-overhangs the column, aims at the soul-sand centre, throws a pearl in, and backs off.

- Scans loaded chunks for chambers and lists them with a ready/occupied verdict.
- Deposits by coordinate, by scan index, or into every empty chamber; configurable pearls-per-chamber.
- Exposes a service API so other automation (e.g. a stash-mover) can drive deposits.

📖 [PearlDrop wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/PearlDrop)

### KitMaker — kit shulker filler *(validated live on 2b2t)*

Mass-produces filled "kit" shulkers from one example template shulker — same items, counts, and slots — over and over, until it runs out of shulkers or materials.

- Reads the template from a **placed example shulker box** (read in place — `.km template auto` auto-detects the nearest one) or from a shulker held in a designated chest.
- Auto-discovers and classifies the floor-level containers around it (empty-shulker source, finished-kit deposit that may already hold kits, item sources) — handles any mix of single/double chests in any rotation.
- Configurable match strictness (item type / ignore cosmetics / exact components); **partial kits** when sources run short; never digs the floor.

📖 [KitMaker wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/KitMaker)

### Regear — resupply from a kit shulker

A one-shot resupply: place an ender chest, pull a named **kit shulker** out of it, empty the kit into your inventory, return the empty shulker, optionally equip the armor and offhand a totem — then turn itself off.

- Matches the kit shulker by custom name or by colour; falls back to a placed ender chest within a radius.
- Optional armor equip, offhand totem, return-the-shulker, player-pause, and disable-when-done.

📖 [Regear wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/Regear)

### Boat — open-water autopilot *(validated live on 2b2t)*

Drives a boat the bot is seated in across open water to a coordinate. There is no real client in a proxy, so it **reimplements Minecraft's `AbstractBoat` physics server-side** — the bot becomes the boat's controlling client and reports its motion via `MoveVehicle`, exactly like a vanilla client rowing.

- `.boat mount` seats the bot in the nearest empty boat; `.boat goto <x> <z>` steers to the target (turn-to-heading + thrust) and stops on arrival; manual `fwd / back / left / right` for hand control.
- The simulated motion is accepted by 2b2t's vehicle anti-cheat — smooth travel, no rubber-banding.

### Bubble-column transport *(validated live on 2b2t)*

A Baritone pathfinder addition: the planner treats **bubble columns as a movement**, so it rides the bot **up a soul-sand column** (and down a magma column, with a magma-guard) as part of a normal path — `.pathfinder goto <x> <y> <z>` can take a water elevator to a higher floor and exit laterally. Works with **sign-held water columns** (the common no-flood build). Exit-timing is functional but not yet smoothed.

### ElytraPilot — autopilot elytra flight *(validated end-to-end in v4.0.0)*

Autonomous elytra flight: deploys, steers, fires fireworks, simulates its own physics to stay on course, routes through the nether, and lands itself — covering long-haul travel, the 2b2t nether-highway bounce, and a full overworld↔nether trip planner. The three headline capabilities are now flown-and-validated live on 2b2t, not first cuts.

- **No-firework nether-highway bounce** — a pure-input ground "bounce" along the 2b2t obsidian highways that **Grim accepts**: ~30–38 b/s sustained, **zero fireworks, zero setbacks**. Self-sustaining over long hauls via a seamless **0-tick elytra hot-swap** and an **ender-chest elytra resupply**, with obstacle stall→pass and centerline tracking.
- **Baritone-style nether flight** — a per-tick **physics-simulation solver** flies the route the way a human "constantly corrects," and native [nether-pathfinder](https://github.com/babbaj/nether-pathfinder) routing plans a path through *unloaded* chunks in ~1s from seed terrain. `fly trip nether <x> <z>` has flown the full leg and **landed on target at full HP, no totems**; 700-block trips proven, plus lava recovery and totem-pop abort.
- **Overworld flight from spawn** — `fly trip <x> <z>` gears up from nothing (no-line-of-sight container open + self-kill relocation + contents-match kit), then flies a distance-scaled **climb / free-glide** cruise and lands dead-on. A full naked-spawn → 25k → on-target landing is done.
- A chunk-loading governor (won't outrun 2b2t's slow streaming), AutoEat fire/heal integration, and a ~40 b/s speed cap throughout.
- ⚠️ *Not yet fully validated:* the **multi-leg saved-route long-haul** — chaining several flight/highway/portal legs across dimensions to a far destination. The individual legs above are flown-and-proven; a complete multi-leg long-haul run is still pending (a first attempt was cut short by a flight-time cap).

📖 [ElytraPilot wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/ElytraPilot)

### Enchanter — anvil auto-enchanting *(⚠️ not yet validated live)*

Auto-builds fully-enchanted "max template" gear in an anvil station: pulls un-enchanted gear and pre-made enchanted books from chests and turns them into god gear one piece at a time, in the **cheapest possible anvil order**, funding the XP itself from a chest of bottles.

- **Cheapest anvil order** — an exact search over all binary combine-trees finds the minimum-XP order that keeps every step under the survival "Too Expensive!" cap (a god sword is ~72 levels optimally vs ~171 naively).
- **XP-bottle charging** — tops up just before each anvil step instead of banking the run up front, using ~2–7× fewer bottles, throwing bottles at its feet and collecting the orbs.
- **Gravity-fed anvil pillar** handling: waits for the next anvil to fall + settle when one shatters.
- Built-in **max templates** for swords, tools, all armor, bow/crossbow/trident/mace/fishing rod/elytra/shield, each with variant picks (damage type, fortune vs silk, protection type, …) — plus shears, flint & steel, brush, and carrot/warped-fungus-on-a-stick, covering **every vanilla enchantable item**. Auto-discovers the station layout within 32 blocks.
- **Optional curses** — Curse of Vanishing (any item) and Curse of Binding (worn gear) are independent per-family opt-ins, **never on by default**.

📖 [Enchanter wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/Enchanter)

### Stash system — scanner + order picker *(new in v3.0.0)*

A stash-organisation bot + payment-gated order picker, built on the proxy's existing Postgres/Redis + Discord layers (no separate Node bot).

- **StashScanner** (`.ss`) — walks a stash of kit shulkers read-only, reads each shulker's contents from its CONTAINER component (never placing one), classifies it as a kit by content signature, and snapshots stock to Postgres. Unreachable chests are skipped + logged; `allowBreak`/`allowPlace` stay off the whole walk.
- **OrderFiller** (`.of`) — pops one **paid** order at a time, withdraws the reserved shulkers (ShiftClick fix), deposits them into the order's outgoing chest, and writes a manifest. Stock drift / unreachable chests surface as shortfalls.
- **Order System** (`.order`) — native order intake (`catalog` / `stock` / `order` / `status` / `cancel`), atomic soft-hold with a TTL reaper, an `orders:paid` Redis adapter for an external shop / TicketTool bot, and a Discord manifest. Stash coordinates never leave the database — customers see only the outgoing chest.

Enable with `database stash on` + `database on`. Tables self-provision on start.

📖 [Order System wiki →](https://github.com/aquariusnetwork9/AquariusProxy/wiki/OrderSystem)

---

## Development

Java 21+ to run; Gradle installs the toolchain it needs to compile.

- `./gradlew run` — build and run a local dev instance
- `./gradlew shadowJar` — build the executable jar to `build/libs/AquariusProxy.jar`
- `./gradlew nativeCompile` — build a GraalVM native image (requires a GraalVM JDK)

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
