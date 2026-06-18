# What's New in 5.0 (beta)

!!! warning "5.0 is a pre-release / beta"

    The 5.0 jars are built by GitHub Actions with a signed [build-provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations) and published on the Aquarius Launcher's **`java.1.21.4`** channel, but 5.0 has **not been promoted to a stable tag** yet. The features marked **⚠️ beta** below are built and deployed but **not yet fully run live on 2b2t** — use them with that in mind and report issues.

The 5.0 line rolls up everything added since the **4.0.0** stable, plus a set of upstream **ZenithProxy stability fixes**.

## Headline features

### Boat autopilot — ✅ live-validated

A server-side reimplementation of Minecraft's `AbstractBoat` physics. Because a proxy has no real client, the bot becomes the boat's controlling client and reports its motion via the `MoveVehicle` packet — accepted by 2b2t's vehicle anti-cheat with no rubber-banding.

* `.boat mount` seats the bot in the nearest empty boat.
* `.boat goto <x> <z>` autopilots across **open water** to the coordinate and stops on arrival.
* Manual `fwd / back / left / right` for hand control.

See [Movement & Transport → Boat control](Movement-and-Transport.md#boat-control).

### Bubble-column traversal — ✅ live-validated *(exit polish pending)*

The Baritone pathfinder now treats **bubble columns as first-class moves**: it rides **soul-sand columns up** and **magma columns down** as part of any normal `b goto` / `b mine` path, including columns fed by sign-held water. The lateral exit can overshoot slightly before the bot steps off — functional, but a polish item.

See [Movement & Transport → Bubble-column traversal](Movement-and-Transport.md#bubble-column-traversal).

### KitMaker — ✅ live-validated

Mass-produces filled "kit" shulkers from one placed template shulker plus a floor of supply chests — same items, counts, and slots — until it runs out of shulkers or materials. Includes empty-stack detection, `template auto` (auto-detect the nearest placed shulker), and **partial-kit** support so kits build even when some sources run short.

See [KitMaker](https://github.com/aquariusnetwork9/AquariusProxy/wiki/KitMaker).

### Backported ZenithProxy stability fixes — ✅

The highest-impact current-breakage fixes from upstream ZenithProxy, ported into AquariusProxy:

* **Disconnect-deadlock fix** — `disconnect()` is re-dispatched off the client event loop so it no longer deadlocks the tick thread.
* **2b2t kick fix** — connections are dropped via a cookie-request packet instead of the old carried-item trick.
* **MCProfile lookup** — the dead Minetools profile API is replaced with MCProfile's Java username/UUID endpoints for whitelist/playerlist resolution.

### GitHub-built pre-releases — ✅

Pre-releases are now built and published by GitHub Actions on ubuntu-latest. A single run builds the shadow jar and publishes a GitHub **prerelease** to both the source `+1.21.4.pre` tag and the launcher's `+java.1.21.4` channel tag, each carrying the `AquariusProxy.jar` asset with a provenance attestation.

## Still in beta

These modules are complete and deployed but **not yet fully validated live on 2b2t**:

| Module | What it does |
| --- | --- |
| **⚠️ PearlDrop** | Throws ender pearls *into* stasis chambers to stock them — the deposit counterpart to PearlPlus. |
| **⚠️ Enchanter** (`/enc`) | Auto-builds god gear in an anvil station in the cheapest anvil-combine order, self-funding XP from bottles. |
| **⚠️ ElytraPilot multi-leg long-haul** | Chains several flight / highway / portal legs across dimensions to a far destination. The individual legs — single-leg nether/overworld flight and the no-firework highway bounce — **are** validated; the full multi-leg run is still pending. |

## Installing / updating to 5.0

Use the **Aquarius Launcher** — it pulls the latest `java.1.21.4` build automatically, so no manual download is needed. See [Setup](Setup.md). Do not download the jar by hand from the Releases page; the launcher manages the version for you.
