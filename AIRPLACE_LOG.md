# AirPlace capture & decode (Rusherhack → AquariusProxy v5.0.0)

Append-only dev log for the AirPlace feature (mirrors EBOUNCE_LOG.md convention).

## #1 — 2026-06-18 — First clean capture (oldmanmango VPS, sniffer `blocks` template)

Captured the user's Rusherhack client airplacing through the proxy as the **controlling**
player. (First attempt returned 0 lines: a stale `sniffFilter='moveplayer'` was AND-ed with
`template blocks` → impossible to match both. Fix = `.aqm sniff filter off`.)

### Decoded technique: OFFHAND-SWAP AIRPLACE

Each airplaced block is a 3-packet sandwich fired in one tick:

```
OUT ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0,y=0,z=0, face=DOWN, sequence=0)
OUT ServerboundUseItemOnPacket(x,y,z, face, hand=OFF_HAND, cursorX,Y,Z=<random 0..1>, insideBlock=true, hitWorldBorder=false, sequence=<n>)
OUT ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0,y=0,z=0, face=DOWN, sequence=0)
```

Invariants for an airplace (vs a normal placement):
- `hand = OFF_HAND` (block held in main hand, swapped to offhand for the place, swapped back → net-zero hand state)
- `insideBlock = true` ALWAYS (claims head-inside-block → server skips reach/LOS; Grim-accepted)
- cursor hit-vec = **pseudo-random floats in [0,1], NOT pinned to the face plane, NOT 0,0,0**
  (e.g. seq36 face=WEST but cursorX=0.845 — impossible for a real raytrace; randomized each place so not a static fingerprint)
- `sequence` = global incrementing prediction counter (shared with all interactions)
- the two SWAP_ITEM_WITH_OFFHAND PlayerAction packets bracket the place (sequence=0 on those)

Contrast — NORMAL placement in the same capture (NOT airplace):
- tower seq38-40: hand=MAIN_HAND, face=UP, insideBlock=false, cursorY=1.0 (face-pinned), climbing Y, no swaps
- seq42: hand=MAIN_HAND, face=WEST, cursorX=0.0 (face-pinned), insideBlock=false, no swaps

### Raw capture (OUT, template=blocks, body on)

```
06:31:26.256  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:26.262  OUT  ServerboundUseItemOnPacket(x=-4958667, y=68, z=-589815, face=WEST, hand=OFF_HAND, cursorX=0.8456632, cursorY=2.4690447E-4, cursorZ=0.5562154, insideBlock=true, hitWorldBorder=false, sequence=36)
06:31:26.270  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:27.507  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:27.508  OUT  ServerboundUseItemOnPacket(x=-4958667, y=68, z=-589814, face=WEST, hand=OFF_HAND, cursorX=0.6286277, cursorY=0.21335128, cursorZ=0.29855886, insideBlock=true, hitWorldBorder=false, sequence=37)
06:31:27.508  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:29.458  OUT  ServerboundUseItemOnPacket(x=-4958666, y=64, z=-589813, face=UP, hand=MAIN_HAND, cursorX=0.87808603, cursorY=1.0, cursorZ=0.20111197, insideBlock=false, hitWorldBorder=false, sequence=38)
06:31:29.997  OUT  ServerboundUseItemOnPacket(x=-4958666, y=65, z=-589813, face=UP, hand=MAIN_HAND, cursorX=0.87808603, cursorY=1.0, cursorZ=0.20111197, insideBlock=false, hitWorldBorder=false, sequence=39)
06:31:30.555  OUT  ServerboundUseItemOnPacket(x=-4958666, y=66, z=-589813, face=UP, hand=MAIN_HAND, cursorX=0.87808603, cursorY=1.0, cursorZ=0.20111197, insideBlock=false, hitWorldBorder=false, sequence=40)
06:31:33.409  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:33.409  OUT  ServerboundUseItemOnPacket(x=-4958663, y=66, z=-589813, face=EAST, hand=OFF_HAND, cursorX=0.60540694, cursorY=0.5790377, cursorZ=0.3418123, insideBlock=true, hitWorldBorder=false, sequence=41)
06:31:33.409  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:34.498  OUT  ServerboundUseItemOnPacket(x=-4958663, y=66, z=-589813, face=WEST, hand=MAIN_HAND, cursorX=0.0, cursorY=0.6663139, cursorZ=0.3326172, insideBlock=false, hitWorldBorder=false, sequence=42)
06:31:36.559  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:36.560  OUT  ServerboundUseItemOnPacket(x=-4958661, y=69, z=-589809, face=SOUTH, hand=OFF_HAND, cursorX=0.33064392, cursorY=0.47969034, cursorZ=0.31681892, insideBlock=true, hitWorldBorder=false, sequence=43)
06:31:36.560  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:37.502  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:37.502  OUT  ServerboundUseItemOnPacket(x=-4958667, y=70, z=-589813, face=WEST, hand=OFF_HAND, cursorX=0.35958076, cursorY=0.6076645, cursorZ=0.869537, insideBlock=true, hitWorldBorder=false, sequence=44)
06:31:37.503  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:38.250  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:38.251  OUT  ServerboundUseItemOnPacket(x=-4958665, y=70, z=-589817, face=NORTH, hand=OFF_HAND, cursorX=0.4481611, cursorY=0.7132718, cursorZ=0.88019234, insideBlock=true, hitWorldBorder=false, sequence=45)
06:31:38.251  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:40.898  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:40.899  OUT  ServerboundUseItemOnPacket(x=-4958662, y=66, z=-589809, face=SOUTH, hand=OFF_HAND, cursorX=0.07516235, cursorY=0.119567946, cursorZ=0.035624005, insideBlock=true, hitWorldBorder=false, sequence=46)
06:31:40.899  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:42.658  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:42.658  OUT  ServerboundUseItemOnPacket(x=-4958663, y=66, z=-589809, face=SOUTH, hand=OFF_HAND, cursorX=0.38319707, cursorY=0.28265736, cursorZ=0.20871608, insideBlock=true, hitWorldBorder=false, sequence=47)
06:31:42.658  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:44.200  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
06:31:44.200  OUT  ServerboundUseItemOnPacket(x=-4958666, y=66, z=-589810, face=SOUTH, hand=OFF_HAND, cursorX=0.0777093, cursorY=0.5450587, cursorZ=0.35874873, insideBlock=true, hitWorldBorder=false, sequence=48)
06:31:44.200  OUT  ServerboundPlayerActionPacket(action=SWAP_ITEM_WITH_OFFHAND, x=0, y=0, z=0, face=DOWN, sequence=0)
```

## #2 — 2026-06-18 — Implemented (internal primitive, compiles, NOT live-tested)

Built the capability internal-only (no passthrough, no chat commands — user's choices):

- **`PlayerInteractionManager#airPlaceOn(int x, int y, int z, Direction face)`** — the reusable
  capability, parallel to `ghostUseItemOn` but using the decoded offhand-swap technique. Block must be
  held in the MAIN hand. Emits `SWAP_ITEM_WITH_OFFHAND` → `UseItemOn(OFF_HAND, insideBlock=true,
  random cursor, via startPrediction for the seq)` → `SWAP_ITEM_WITH_OFFHAND`, all in one synchronous
  call → atomic within a tick. Returns false if suppressed.
- **Config `client.extra.airPlace`**: `enabled` (master switch, DEFAULT OFF), `randomizeCursor`
  (default true), `yieldToAutoTotem` (default true).
- **AutoTotem conflict — resolved**: (1) atomic same-tick emission so AutoTotem (tick-boundary scheduled,
  owns offhand) never sees the half-swapped state; (2) a totem pops from EITHER hand, so the totem is
  never absent from both hands during the sandwich; (3) `yieldToAutoTotemThisTick()` defers the place on
  any tick AutoTotem would act (`autoTotem.enabled && health <= healthThreshold`). Net: no conflict at
  full HP, clean priority yield for low-HP combat building.

NOT wired to any auto-consumer — `useItemOn` (Bot.java input path) only fires on a real raycast block hit
so it can't reach open air, and blanket-routing `ghostUseItemOn` would break container opens. The primitive
is exposed for a future feature (tower/bridge/scaffold/obsidian) or an explicit caller to drive. NOT yet
live-tested on 2b2t. `compileJava` green.

## #3 — 2026-06-18 — AutoPortal (first airplace consumer; primitive, no command; compiles, NOT live-tested)

Built the auto-portal capability as a PRIMITIVE (user: "this should just be a primitive as well" — like airplace,
no chat command). `PlayerInteractionManager#airPlaceOn` refactored: public **`emitAirPlace(x,y,z,face)`** now does the
swap-sandwich (bypasses the `airPlace.enabled` master switch, still honors AutoTotem-yield + randomizeCursor); public
`airPlaceOn` = the gated wrapper. AutoPortal calls `emitAirPlace` directly (owns its own enable gate).

- **`module/impl/AutoPortal.java`** — registered Module, programmatic entry `start()` (plan+build at current
  pos/facing) / `cancel()`. Ticked state machine IDLE→PLACING→LIGHTING→DONE/FAILED. enabledSetting()=false (never
  auto-on); `start()` calls `enable()`. NO command (deleted PortalCommand per the user).
- **Geometry**: minimal CORNERLESS frame (10 obsidian), vertical plane `buildDistance` blocks ahead, width axis ⊥ to
  facing (facingFromYaw), base at feet level (bottom row on the ground at oy-1). Build order makes every cell place
  against a placed neighbour/ground EXCEPT 3 genuinely-floating cells (the two side-column bottoms next to the omitted
  bottom corners + the first top-row block) → those use true place-into-air `(target=cell, face=DOWN)`. **This air-
  placement coordinate behaviour for unsupported cells is the #1 live-test risk** (placed-position semantics couldn't
  be verified from packets alone; a full-corner 14-block frame is the robust fallback if it's finicky live).
- **Materials**: aborts up front if <10 obsidian or no lighter. `ensureHeld(id)` selects/moves obsidian then the
  lighter into the main hand (SetHeldItem / MoveToHotbarSlot). emitAirPlace consumes 1 obsidian per cell.
- **Lighting**: `ghostUseItemOn(bottom-col1 obsidian, UP, MAIN_HAND)` with the lighter held → fire in the interior
  bottom → ignites. Stops after lighting (build+light only; no walk-through, user's choice).
- **Config `client.extra.autoPortal`**: buildDistance=2, placeIntervalTicks=3, useFireCharge=false, timeoutTicks=200.

`compileJava` green. NOT live-tested. To drive it, a caller invokes `MODULE.get(AutoPortal.class).start()` — no command
exists, so a test trigger needs wiring (or temporarily add one).
