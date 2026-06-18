# E-Bounce Attempt Log (AquariusProxy ElytraPilot)

**Standing order (2026-06-14):** every attempt, every approach, every failed test on the e-bounce
MUST be logged here, so the same broken fixes are never re-attempted. Append-only. Newest at the bottom.

**Goal:** fast NO-FIREWORK nether-highway travel at ~40 b/s, replicating Rusherhack's ElytraFly bounce,
accepted by 2b2t (Grim movement-prediction anticheat). 2b2t is sensitive to fast-movement exploits but
accepts e-bouncing at ~40 b/s when the trajectory is vanilla-physics-plausible.

**Code:** `module/impl/ElytraPilot.java` `tickBounce` + config `client.extra.elytraPilot.*`.
Branch `stash-system-v3`. Bot = oldmanmango. tmux session = **goldfarm** (NOT "oldmanmango").

---

## The proven physics constraints (read before proposing ANY fix)

1. **Velocity injection from a standstill structurally CANNOT pass 2b2t.** Proven 2026-06-14 by capturing the
   BOT's own outbound packet stream. Two server behaviours kill it:
   - Every server position-correction carries `deltaX/Y/Z=0` → the server **zeroes the bot's server-side
     velocity every tick**.
   - 2b2t/Grim **validates elytra horizontal speed against glide physics** — it checks each tick's velocity is
     a plausible delta from the PREVIOUS velocity per `travelFallFlying`. A near-level glider at ~0 speed is not
     allowed to suddenly move 1.8 b/t, so the first injected frame trips a correction → velocity zeroed → death
     spiral, pinned at the anchor X forever. A ramp does NOT help: once corrected, velocity is back to ~0, so the
     next injected frame is implausible again.
   - The Rusherhack client escapes ONLY because it is ALREADY MOVING when the bounce starts (its motion stays
     within Grim's tick-to-tick tolerance and its tracked velocity builds). The bot starts from rest on the road.
2. **Speed survives a ground touch ONLY if fall-flying stays active through it.** `travelFallFlying`
   (Bot.java:780-805) applies only **0.99** horizontal drag. `travelInAir` on the ground applies
   `floorSlipperiness*0.91 ≈ 0.546` friction (Bot.java:807-814) — a ~45% speed loss per grounded tick that is
   NOT fall-flying.
3. **The legit way to GAIN forward speed without fireworks is the dive-conversion term** in `travelFallFlying`:
   when `velocity.y < 0`, `m = velocity.y * -0.1 * cosPitch` is added along the look direction (Bot.java:792-795).
   Diving converts fall speed → forward speed, and Grim's own sim agrees with it, so it is accepted. This is the
   only physically-plausible acceleration source the bot can use.
4. **Vanilla auto-jump bounce mechanics** (Bot.java): holding jump on ground with `noJumpDelay==0` → `jump()`
   (vy=0.42, +sprint kick 0.2) and sets `noJumpDelay=10` (Bot.java:396-399) — the 10-tick cooldown IS the bounce
   period. The jump-EDGE auto-deploy (Bot.java:374) fires START_ELYTRA_FLYING once per press edge only, so a held
   jump needs an explicit re-deploy each cycle.

---

## Attempt history

| # | Ver | Date | Approach | Result |
|---|-----|------|----------|--------|
| 1 | v3.27.0 | 06-14 | pitch-0 FLAT velocity injection ("driven skim") | RUBBERBAND — flat trajectory unnatural, server lagback-pins every tick |
| 2 | v3.27.1 | 06-14 | physics auto-jump bounce, no injection | UNTESTED — the trip/Discord panel re-armed tripActive and hijacked the pilot into a 0,0 firework cruise; never actually exercised the bounce |
| 3 | v3.27.2 | 06-14 | injection + PINNED flat altitude (bounceSkimHeight hover) | RUBBERBAND — flat contradictory-onGround trajectory; bot stuck onGround → deploy gate never fires → grounded-speeder loop |
| 4 | v3.27.3 | 06-14 | captured-recipe: auto-jump parabola + horizontal-only injection + pitch75 | PARTIAL — see #5; injection still the core |
| 5 | v3.27.4 | 06-14 | 1-tick ground DWELL + injection EVERY tick + persistent ramp + setback-hold + bouncePitch live=2 | **VERTICAL ACCEPTED** (server tracks Y up to apex, fall-flying registers server-side, metadata 0x80 confirmed). **HORIZONTAL STILL CLAMPED** (corrected X creeps ~0.034 b/t ≈ 0.7 b/s). NOT WORKING. This is the current HEAD (commit a8ce285af) + uncommitted working-tree edits. Root cause = constraint #1 (injection). |

### What attempt #5 DID fix (keep these — real progress)
- The **1-tick ground dwell**: the bot used to `vel.setY(0.42)` the SAME tick it touched the road, so it never
  emitted an `onGround=true` packet → server read +0.42 as illegal mid-air flight → rejected the jump entirely.
  Fix: on road touch emit `onGround=true` with no impulse for one tick, THEN jump the next tick (a legal vanilla
  ground-jump). After this the server ACCEPTS the full vertical bounce and fall-flying registers server-side.

### Diagnostic method that cracked the root cause (reusable)
- The built-in `AquariusSniffer` default mode captures EVERY packet (empty template+filter, dir=both). It
  registers on the global CLIENT_REGISTRY (enable while logged-out, live on connect). `handleOutgoing` =
  the BOT's own packets, `handleInbound` = server's. Setup:
  `aquariusminer sniff template off / filter off / dir both / body on / live on / clear / on` → streams to
  `log/latest.log`. Drive the bounce with NO controlling client (console `connect` → wait spawn → `fly off` /
  `fly highway e` / `fly on`) so you capture the BOT, not a relayed client.
- **CONFOUND that wasted a cycle:** a controlling client (e.g. shallowplague) RELAYS through the proxy, so a
  "bounce" with the user's client connected captures the USER, not the bot. ElytraPilot logs nothing and the
  pitch shows the human look (~2°) when it is the client. Always test bot-only via console `connect`.
- Grep at read time: `OUT.*MovePlayerPos` / `IN.*ClientboundPlayerPositionPacket` /
  `SetEntityDataPacket(entityId=<self>`. Unfiltered+body floods on connect (chunk/light/entity) — keep bounces short.

---

## What the REAL Rusherhack e-bounce sends (the replication target, from a live capture)
- Horizontal: Δx ≈ 2.0 b/t (peak 2.13) ≈ 40 b/s, sustained; bleeds 2.13→1.95 over a glide (the 0.99 drag) then
  restores at the bounce.
- Vertical: smooth parabola y = roadY ↔ roadY+0.89, ~9-10 tick cycle; vanilla jump (vy≈0.42) decelerating at
  gravity ~0.08/tick. `onGround=true` for exactly ONE packet at the bottom.
- Rotation: **pitch = 75.0** (PitchSpoof) + yaw locked to the road axis.
- Packets: ONLY `ServerboundMovePlayerPosPacket` per tick + `ServerboundPlayerCommandPacket(START_ELYTRA_FLYING)`
  ~2/s (once per cycle on the way up). ZERO sprint/input packets. The real client injects motion client-side; the
  server only sees the resulting positions, which stay within Grim tolerance because the client was already moving.

---
<!-- Append new attempts below this line. -->

---

## Attempt #14 — THE JUMP-INPUT HANDSHAKE (2026-06-14) — decoded the deploy from the client capture; partial fix in

**Rusherhack client jar is NOT on disk** (loader `rusherhack-loader.jar` + `RusherHackInstaller.jar` only; the
client is memory-loaded/encrypted). BUT its **PacketLogger** logs every packet to
`...PrismLauncher/instances/1.21.4/minecraft/rusherhack/logs/packetlogger/`; `1781414328576.log` has a full real
e-bounce. Parser + decoded cruise saved in `auto-miner/tools/{parse_rusher_packetlog.sh,rusher_ebounce_capture_decoded.txt}`.

**THE REAL E-BOUNCE IS AN AIRBORNE GLIDE, NOT A GROUND BOUNCE** (re-confirmed): onGround=false the entire flight,
level pitch (~0), continuous fall-flying (never toggled), y≈roadY+1 with a gentle ±0.17 porpoise, steady ~33 b/s
held by injection.

**THE DECODED LIFTOFF/DEPLOY HANDSHAKE (the missing piece):**
```
[jump tick]  ServerboundPlayerInputPacket { jump=true }      <-- a REAL jump INPUT
             ServerboundMovePlayerPacket.Pos { y=roadY+0.42, onGround=false }
[~3 ticks later, y≈roadY+1]  ServerboundPlayerCommandPacket { action: START_FALL_FLYING }
[then] horizontal injection 0->1.66 via v += (target-v)*0.5 ; cruise (inputs steady -> no more PlayerInput)
```

**ROOT CAUSE of every prior failure (DEFINITIVELY proven this session):** the bot never sent
`ServerboundPlayerInputPacket(jump=true)`. Synth (direct position) and velocity-injection both BYPASS the input
pipeline, so **Grim never simulates a jump, thinks the bot is still grounded, and REFUSES `START_FALL_FLYING`** ->
fall-flying never registers server-side -> the server pins the bot as a walking player and rubberbands every move.
Proof: **idle/disarmed = 0 teleports; armed = server teleports the bot to its exact spawn pos EVERY tick.** So it is
purely the bounce code path, not lag/distance/chunks (those were red herrings — disproved: idle=0 teleports).

**FIX (in, partial):** `tickBounce` now does the LIFTOFF with a real jump INPUT (`submitMove(jump=true)` while
onGround -> Bot.tick emits ServerboundPlayerInput(jump=true) + jumps), deploys `START_FALL_FLYING` once well airborne
(y>roadY+0.5), then injects horizontal `v += (target-v)*bounceBoostFactor` + a gentle altitude porpoise. Synth
(Bot.tickEBounce) abandoned (Grim rejects un-inputted/teleported movement). bouncePitch default 0.

**RESULT of the fix:** improvement but not done — the bot now MOVES (~1-2 b/s) and attempts fall-flying (vs
pinned-at-0 before), BUT is still capped at ~2 b/s (walk/sprint speed) with continued setbacks => **the server is
STILL refusing the elytra deploy.** Consistent with the user's note that the bot's **elytra is LOW** (server won't
grant fall-flying on a near-dead elytra; local canGlide can still optimistically set 0x80, hence ff=true locally).

**NEXT (highest priority):** put a FRESH full-durability elytra (+ a spare) on the bot, then retest the same build.
Expect: jump input -> START_FALL_FLYING ACCEPTED (fall-flying registers) -> injection to ~33 accepted -> clean glide.
If it STILL caps at walk speed with a fresh elytra, sniff inbound `ClientboundSetEntityData` for the self entity to
confirm whether the server ever sets the 0x80 fall-flying flag, and capture a fresh Rusher bounce THROUGH the proxy
for a direct tick-by-tick diff of the deploy handshake.
Bot state: disarmed, on the East nether highway ~x93707 y120; current build = airborne-glide + jump-input liftoff,
bouncepitch 0 / bouncespeed 33 / ebounce on / bouncedebug on (persisted). tmux session goldfarm.

---

## Attempt #13 — DECODED the real e-bounce from the client's own PacketLogger → AIRBORNE-GLIDE rewrite (2026-06-14)

**The Rusherhack client jar is NOT on disk** (loader + installer only; the client is memory-loaded/encrypted by the
loader — anti-piracy). BUT Rusherhack's **PacketLogger** writes to
`...PrismLauncher\instances\1.21.4\minecraft\rusherhack\logs\packetlogger\` and `1781414328576.log` (22 MB) had a
real e-bounce: **1715 highway-altitude (y≈120) outbound move packets.** Parser + decoded capture saved to
`auto-miner/tools/parse_rusher_packetlog.sh` + `auto-miner/tools/rusher_ebounce_capture_decoded.txt`.

**WHAT THE REAL E-BOUNCE ACTUALLY IS (this overturns every prior assumption):**
- **`onGround` = false the ENTIRE flight. There is NO ground bounce, no jump-per-cycle, no road touch.** The name
  is a misnomer — it's a continuous AIRBORNE glide.
- **Pitch ≈ 0° (level)**, not 75° and not +2°. Fall-flying stays ON continuously (never toggled).
- Flies at **y≈roadY+1** (just above the road) with a gentle **±0.17 b** porpoise: a slow injected climb
  (~+0.010/tick) then a natural fall-flying sink, repeat (~24-tick period).
- Horizontal is a steady **~1.66 b/tick (33 b/s)** with a small bleed-restore tied to the porpoise.
- **Spinup (decoded):** (manual ground sprint to center, ends ~stopped) -> ONE vanilla jump (vy 0.42, og->false)
  -> deploy elytra at apex -> **inject horizontal AIRBORNE, ramping 0->1.66 over ~8 ticks with the smoothing
  `v += (target - v)*0.5`** (gap halves each tick: 0.85,1.26,1.47,1.57,1.66) -> hold. NO fireworks.

**THE KEY INSIGHT:** cold-start horizontal injection IS accepted by Grim -- *as long as it is done AIRBORNE with
unbroken fall-flying.* Every prior failure (per-tick inject, sprint, synth, ground-bounce) wrapped the injection in
a repeated GROUND BOUNCE (onGround toggling + dwell), and THAT implausible motion is what got rubberbanded -- not
the injection itself. The horizontal injection (#11 bleed-and-restore) was actually close; it was the bounce
around it that tripped Grim.

**REWRITE DONE:** `tickBounce` is now the airborne-glide model: LIFTOFF (one jump) -> DEPLOY -> continuous airborne
glide (level pitch, never touch ground, never toggle fall-flying), with `v += (target-v)*bounceBoostFactor` (0.5)
horizontal injection ramping then holding, and a vertical porpoise band `[roadY+bounceHoverLow(0.8), roadY+
bounceHoverHigh(1.4)]` (climb via `bounceClimbVel`(0.03), sink via physics). Setback/frontier eases injection off.
Removed: the ground bounce (dwell/jump-per-cycle/onGround toggle), the Firework/Sprint/Synth kick paths from the
main route (Synth branch + Bot.tickEBounce left as dead code). `bouncePitch` default 75->0. New config
`bounceHoverLow/High`, `bounceClimbVel`, `bounceBoostFactor`; new cmds `fly bouncehover/bounceclimbvel/bounceboost`.
Compiles clean; jar built. **NOT yet live-tested** (bot is still stuck deep-east at ~x96923 and needs relocating
to a loaded area first).

**Live-test plan:** relocate bot to a near-spawn highway -> `fly highway e` (or w) -> `fly bouncepitch 0` (config
may still hold 75) -> `fly bouncedebug on` -> `fly on`. Expect: one jump, deploy, smooth ramp to ~33 b/s, steady
airborne glide at y≈121, ZERO/near-zero teleports. Tune: `bouncespeed` (try 33 then up toward 40), `bounceboost`
(ramp smoothness), `bouncehover`/`bounceclimbvel` (porpoise).

---

## Attempt #12 — TUNING the bleed-and-restore (2026-06-14) → lower speed helps; restore-shape test blocked by a stuck bot

**Live speed sweep (no rebuild, `fly bouncespeed` live):** teleports/resets scale DOWN with speed —
~8 per 15s @33 b/s -> 3 @24 -> 2 @18 -> 2 @14. So the restore-deviation that trips Grim shrinks with the speed
(smaller per-cycle drag loss to restore). ~18-24 b/s gives ~4x fewer resets than 33 = cleaner low-firework cruise,
at the cost of speed. (Caveat: speeds didn't fully settle in 15s windows; trend is clear though.)

**Restore-SHAPE lever added (rebuild):** `bounceRestoreTicks` (spread the per-cycle restore over N ticks after
liftoff so each tick's deviation is smaller/under Grim's per-tick tolerance) + `bounceRestoreMax`, both live-tunable
(`fly bouncerestoreticks`, `fly bouncerestoremax`). Rationale: #9 per-tick (every tick) pinned; #11 one sharp tick
(0.17) = 0.6 resets/s; a short 2-4 tick burst might be the sweet spot. **NOT conclusively tested** — see below.

**BLOCKER (self-inflicted): the bot flew ~16k blocks east across all the tests and is now at x~96923 (~775k
overworld), an effectively-ungenerated deep-nether region. After the deploy-restart reconnect its own chunk won't
stream, so ElytraPilot.onTick hits the chunk-load guard (Bot.java:359 `!World.isChunkLoadedChunkPos -> return`) and
NO phase runs — the bot just sends idle keepalive position packets, frozen, pitch 2 (not the bounce's 75).** A
35s wait and a full disconnect+reconnect did NOT recover it (chunk still doesn't load this deep). The bounce can't
self-recover because the chunk guard blocks before any phase (bounce OR cruise) can move it west. Bot DISCONNECTED
safely (no death, gear intact), parked at ~x96923 y120 East nether highway.

**Consequences / next:**
- Need to RELOCATE the bot to a near-spawn highway (well-loaded) for any further live testing — deep-east is
  unusable (chunks won't stream post-reconnect). Recovery idea: a cruise/native-route leg west, but the chunk guard
  blocks it too; may need a one-off guard bypass or a manual real-client relocation, or accept the gear is parked.
- The restore-shape sweep (`bouncerestoreticks` 1 vs 3 vs 6) still needs a clean run in a LOADED area.
- Surest path to a fully-clean bleed-and-restore remains the CAPTURE (match the real client's exact restore shape).

---

## Attempt #11 — BLEED-AND-RESTORE (per-cycle restore, matching the capture) (2026-06-14) → BIG IMPROVEMENT, mostly works

HOLD changed from per-tick injection to the captured real-client pattern: let real 0.99 drag bleed the speed
across each glide (so MOST ticks match Grim's drag prediction exactly), and inject ONE small restore per cycle on
the first airborne+flying tick, sized to bring speed back to goal (`goal/0.99 - hsp`, capped `BOUNCE_RESTORE_MAX`
0.30; natural value ~0.17, matching the capture's +0.18). Firework KICK still spins up to the HOLD-enter speed
first. Config for the test: `bouncekick firework`, `bouncespeed 33`, `bounceholdenter 0.9`, `bounceholdexit 0.75`.

**RESULT = best yet, mostly works but not clean.** Flight x88401 -> x89965 = **~1564 blocks at ~24 b/s NET**,
real forward progress (the "Server teleport" positions advance MONOTONICALLY forward = forward position-syncs, not
backward pins). **71% of ticks in firework-free HOLD** (586 HOLD vs 234 KICK). **40 fireworks for 1564 blocks =
1 per ~39 blocks** (vs firework-cruise's ~1 per 24). BUT **~39 hard resets** (spd->0) over the flight (~0.6/s):
the per-cycle restore is mostly accepted but still trips Grim ~0.6/s, and each reset costs a firework re-KICK.

**Significance:** this is the FIRST variant where no-firework HOLD travel actually works — a step change from
inject-hold (pinned ~8 b/s, 33 resets/22s) and synth (339 teleports). It validates the bleed-and-restore model.
The remaining ~0.6/s resets are the gap between our restore and the real client's: same magnitude (+0.17 vs
+0.18) but ours trips Grim and theirs doesn't, so the DIFFERENCE is in the exact timing/shape of the restore (and
possibly the onGround/fall-flying micro-timing around the bounce).

**NEXT (the capture):** to close the gap, capture the user's real working Rusherhack e-bounce through the proxy
sniffer at full per-tick detail and match the restore's exact tick-offset/shape + the exact onGround &
START_ELYTRA_FLYING timing. Tuning knobs to try meanwhile: `BOUNCE_RESTORE_MAX` down to ~0.18; restore spread over
2-3 ticks just after liftoff instead of one tick; lower `bouncespeed` (less restore needed -> fewer resets).
Bot left disarmed at ~x89965 y120 East nether highway.

---

## Attempt #10 — SYNTH (byte-for-byte synthesized MovePlayerPos stream) (2026-06-14) → can't bootstrap; rejected

Per the user (this has been done on private proxies), bypass the physics engine entirely and emit a byte-for-byte
copy of a real ElytraFly client's stream: `Bot.tickEBounce()` synthesizes position directly (vanilla jump
parabola vy0.42/g0.08, smooth horizontal ramped 0->target, rotation locked so only MovePlayerPos is sent,
fall-flying TOGGLED — cleared on the 1-tick ground touch, redeployed on liftoff). New mode `bounceKickStart=Synth`.

**RESULT = rejected, can't bootstrap.** From a standstill the bot bounces in place (cmd horizontal = 0 while the
ramp tries to build) and the server setbacks EVERY tick (`setbackHoldTicks` pinned at 15, **169-339 teleports in
12-22s**), which holds the ramp at 0 forever — a catch-22: a stationary/slow elytra bounce is implausible, the
rejection prevents building the speed that would make it plausible. (First build held fall-flying continuously =
instant reject; toggling it fixed that but the stationary-bounce rejection remained.)

**Cross-check with the nxg-org/mineflayer-physics-utils elytra physics (community ref, "tuned for Grim"):** the
math is IDENTICAL to Bot.java travelFallFlying (same gravity/dive/lookdown/align terms, 0.99/0.98/0.99 drag,
firework boost `look*0.1+(look*1.5-vel)*0.5`). So the bot's physics are already correct; the only thrust term in
the model is the firework. This CONFIRMS the wall: Grim enforces 0.99 horizontal drag, so no-thrust speed
(maintained by injection OR by a synthesized constant-speed stream) diverges from its prediction -> setback.

**Why the real client still works (hypothesis):** the capture showed speed BLEED 2.13->1.95 over a glide (= pure
0.99 drag, 9 ticks) then RESTORE +0.18 at the bounce. So the real client mostly matches drag and injects only a
small per-cycle restore that fits inside Grim's (generous, because firework timing is unpredictable) elytra offset
tolerance. The bot's failures all injected differently — per-tick (hold), continuous-ramp, or from a standstill —
and/or tripped an immediate hard flag. NOT yet tried: real-physics glide (let drag bleed) + ONE small restore
injection per cycle AT the bounce, sized to the exact drag loss (the captured pattern). That is the only remaining
no-firework variant consistent with everything.

---

## Attempt #9 — DECISIVE inject-HOLD test (2026-06-14) → INJECT-HOLD IS DEAD. E-BOUNCE CANNOT SAVE FIREWORKS.

To test whether inject-hold sustains from a CLEAN firework-established speed (the only "already moving" case left),
made the HOLD thresholds tunable (`bounceHoldEnterFrac`/`bounceHoldExitFrac` config + commands) and lowered the
handoff to 0.78 so the firework KICK (which plateaus ~33) would hand off to inject-HOLD at ~31. Bot on the East
nether highway, firework mode, bot-only.

**RESULT = RUBBERBAND. Inject-hold fails even from a clean ~31 b/s.** The telemetry cycle repeated every ~6 ticks:
firework boosts to 31-33 → enters HOLD for exactly ONE tick → server **teleports the bot back to spd=0, og=true,
y=120** (full reset to a grounded standstill) → re-KICK (fire) → 31-33 → HOLD → reset … **33 server teleports in
22 s, 23 fireworks burned, net only ~8.7 b/s** (1172 blocks in ~135 s). The ONLY change from the clean 33 b/s
firework run (attempt #8, ZERO teleports, 3000 blocks) is that this build ENTERS HOLD — so inject-hold is
unambiguously the cause. The instant fireworks stop and injection takes over, Grim rejects it and slams the bot back.

**CONCLUSION (definitive, closes the e-bounce investigation):**
- Velocity injection is rejected by 2b2t/Grim in EVERY tested regime: from a standstill (#1-5), from a
  collapsed-low moving state (sprint #8), AND from a clean high firework-established speed (#9). The "you have to
  be already moving" hypothesis is FALSE for the bot — being in motion does not make the injection acceptable.
- The ONLY server-accepted thrust is REAL fireworks. The bounce vertical (dwell→jump→redeploy) is accepted; the
  horizontal can only be sustained by fireworks, at which point it is just firework travel (1 rocket / ~24 blocks,
  attempt #8) with NO savings — and trying to save fireworks (HOLD) makes it rubberband and burn MORE.
- **Therefore: no-firework / low-firework fast e-bounce is impossible for the bot. Firework cruise (climb high,
  long glide) is the only viable fast travel and is more efficient (higher speed at altitude, no bounce overhead).**

**DO NOT re-attempt ANY velocity-injection bounce** (standstill, moving, or hold-maintenance — all proven dead).
Recommendation: retire the e-bounce as a travel tool; use the existing firework cruise. Code left in place
(firework KICK works as a firework-bounce but is pointless vs cruise); `bounceHoldEnterFrac` restored to 0.95 so
inject-HOLD never auto-engages. Bot left disarmed at ~x88396 y120 on the East nether highway.

---

## Attempt #8 — FIRST LIVE TEST of kickstart+hold-inject on oldmanmango (2026-06-14, East nether highway)

Deployed the dev jar via scp (`launcher/ZenithProxy.jar`, banner still labels 3.27.4 — verify by the new
`fly bouncekick` command, not the banner). Bot connected on the East nether highway (z=0, y120), trip disarmed,
sniffer off, `bouncedebug on`. Test: `fly highway e` → `fly on`, bot-only (no controlling client).

**BUG found + fixed mid-session (the RUN-exit):** Sprint mode's RUN gate compared `BOT.getVelocity()` to the
run-start speed, but on the GROUND friction makes the stored velocity read far below the real movement, so the
bot sprinted ~1100 blocks at 5.6 b/s and NEVER deployed (stuck in RUN). Fix: decisions now use the MEASURED
per-tick displacement (`speed` = what actually moved, = what 2b2t sees), not the stored velocity; injection is
also gated to airborne ticks (on the ground the stored velocity is friction-mangled and the jump owns the motion).
Rebuilt + redeployed.

**SPRINT result = FAIL (rubberband, never builds).** Telemetry: RUN reaches ~5 b/s, exits, but then the bot
oscillates 1.4 ↔ 8 b/s and never climbs. Each cycle it spends 2-3 ticks ON THE GROUND where friction halves the
speed (4.7→2.6→1.4) and (correctly) no injection runs, so the ramp counter keeps RESETTING. Server teleports
fire every ~3s; net progress ~1.7 b/s. **Why:** because the speed collapses to ~1.4 b/s on the ground each cycle,
the ramp injection is effectively COLD-STARTING from low speed every cycle — i.e. the proven-dead regime, just
re-entered each bounce. The "already moving" advantage never materialized because the bot couldn't STAY moving.

**FIREWORK result = SUCCESS at ~31-33 b/s, NO rubberband.** Telemetry: `spd` steady 30-33.7 b/s, bounce parabola
y 120.0 ↔ 120.73, `fire=true` periodically. **ZERO server teleports during the whole firework window.** Net
position: x 84250 → 87223 = **~3000 blocks in ~96 s ≈ 31 b/s REAL net progress**, clean. This also PROVES the
dwell→jump→redeploy bounce VERTICAL is server-accepted (same bounce as sprint, but zero teleports) — the sprint
rubberband was purely the low-speed injection, not the bounce.

**It plateaued at ~33 (never reached the 38 b/s HOLD-enter threshold), so it stayed in firework KICK the whole
time and the maintenance hold-inject was never exercised.** boostMinSpacingTicks=14 equilibrates the
firework-bounce at ~33. To reach 40 + engage hold-inject: fire harder (lower the firework spacing) to cross 38,
OR lower the HOLD-enter threshold so inject-hold takes over from the clean ~33 b/s firework-established speed
(that IS the proper "already moving" test of hold-inject — the bot is server-tracked at 33, so injecting up from
there is the best case, unlike sprint which injected from a collapsed 1.4).

**DO NOT re-attempt:** Sprint mode as-is (ground friction collapses speed → injection cold-starts each cycle →
rubberband). A Sprint fix must keep the bot AIRBORNE/moving (minimize ground contact) so injection never restarts
from low speed.
**NEXT:** push firework KICK past 38 to hand off to hold-inject (minimal-firework cruise at 40), and/or reuse the
firework-established speed as the launchpad for injection instead of the sprint run.

---

## Attempt #6 — OFFLINE PHYSICS SIMULATION (2026-06-14, no live risk)

**Hypothesis tested:** can a PURE-PHYSICS (no velocity injection) flat-road bounce reach/hold ~40 b/s, using
only legit forward-energy sources Grim accepts — the sprint-jump kick (`jump()` +0.2 b/t along yaw per jump,
Bot.java:646-650) and the dive-conversion term (`travelFallFlying` vy<0 → forward, Bot.java:792-795)?

**Method:** `auto-miner/tools/BounceSim.java` — the exact Bot.java tick/jump/travelFallFlying/travelInAir
physics ported verbatim. Holds forward+jump+sprint, redeploys elytra each cycle, integrates+collides with the
road. Swept pitch 0..75; also a decay test starting from a 40 b/s kickstart. (Validated against a known data
point: sim peakY at pitch 75 = 0.87 b vs the real-capture 0.89 b — vertical physics confirmed correct.)

**RESULT (decisive):**
- **Natural equilibrium from rest ≈ 7.4 b/s** (best, at pitch 0-5). Falls with steeper pitch (pitch 75 → 4.3 b/s).
  Peak bounce height at the fast pitches is ~2.5 b (not the captured 0.9 b — because the real client's 0.9 b at
  40 b/s is INJECTED, not gravity-fed).
- **A 40 b/s kickstart DECAYS almost immediately:** pitch 2 → 20 b/s after 1 s, 13.6 after 2 s, 8.6 after 5 s,
  6.8 after 20 s. The bounce CANNOT hold 40 b/s; above ~8 b/s the 0.99 drag dominates the per-cycle energy input.

**CONCLUSION (proven, not speculative):**
1. The pure-physics no-injection flat-road e-bounce is a **~7-8 b/s technique. It cannot reach OR sustain 40 b/s.**
2. The real Rusherhack 40 b/s bounce (pitch 75, 0.9 b height) is **velocity injection** — the physics give only
   4.3 b/s at that pitch. 2b2t accepts the real client's injection ONLY because it is already moving (within
   Grim's tick-to-tick tolerance); a bot injecting from rest trips the first correction (constraint #1).
3. Therefore a **no-firework 40 b/s e-bounce for the bot is physically impossible on a Grim server.** Fast travel
   needs continuous fireworks (= the existing firework cruise) OR a fundamentally different injection regime.

**The one untested idea consistent with all proven constraints:** every prior attempt INJECTED FROM REST (cold
start). Nobody has tried **reaching 40 b/s LEGITIMATELY first (a firework boost while fall-flying, so Grim is
already tracking real forward velocity), THEN injecting only the tiny per-tick drag top-up (~0.02 b/t/tick) to
HOLD it.** Maintenance-injection at speed is a small plausible delta (no position-corrections in steady legit
flight → no velocity-zeroing), unlike cold-start injection. NOT yet tried; would need one live test. This is NOT
"no firework" (needs ≥1 kickstart rocket), but it is the only injection regime not already proven dead.

**DO NOT re-attempt:** injection from a standstill at ANY pitch, with ANY ramp (proven dead, attempts #1-5).
**DO NOT re-attempt:** expecting the pure-physics bounce to exceed ~8 b/s (proven by sim).

---

## Attempt #7 — KICKSTART + HOLD-INJECT (2026-06-14, IMPLEMENTED, PENDING LIVE TEST)

Two selectable ways to reach speed LEGITIMATELY first, then a shared maintenance HOLD. The whole point: never
inject from a standstill/disturbed state (proven dead); only ever maintenance-inject the tiny drag top-up once
already moving at speed (the regime the real client uses + the server accepts). Config enum `bounceKickStart`:

**Firework (default):** bounce + fire REAL rockets at a shallow `bounceKickPitch` (10°, boost goes horizontal)
until ≥95% of `bounceSpeed`, then HOLD (no rockets). Robust; costs a few rockets to spin up + on any recovery.

**Sprint (the user's "you have to be running to start the bounce"):** RUN on the ground (forward+sprint, no
elytra) to `bounceRunStartSpeed` (5 b/s), THEN deploy + RAMP the injected speed up FROM that moving state at
`bounceRunRampPerSec` (25 b/s per s) to target, then HOLD. No fireworks. The "already moving" start is what (per
the user's experience, true even on old Rusherhack) keeps the ramp within Grim's tolerance — the standstill ramp
did not. Speed collapse (a swap/hiccup, hsp < 0.6×runspeed) restarts from the ground RUN.

**Shared HOLD:** no fireworks; each tick `add = clamp(goal/0.99 - hsp, 0, bounceMaxInjectPerTick=0.06)` along the
heading — only the ~0.02 b/t the 0.99 drag eats. Capped so a glitch can't snap into a cold-start jump. A setback
(`setbackHoldTicks>0`) or the chunk frontier drops out of HOLD and injects NOTHING until resynced, then re-kicks.
Vertical = the proven-accepted dwell→jump→redeploy parabola; yaw locked to road axis; HOLD pitch = `bouncePitch`.

**New config:** `bounceKickStart` (Firework/Sprint), `bounceKickPitch` (10), `bounceMaxInjectPerTick` (0.06),
`bounceRunStartSpeed` (5.0), `bounceRunRampPerSec` (25.0).
**New commands:** `fly bouncekick <firework|sprint>`, `fly bouncekickpitch <deg>`, `fly bounceinject <bpt>`,
`fly bouncerunspeed <bps>`, `fly bouncerunramp <bps>`. Telemetry label (`bouncedebug on`): KICK / RAMP / HOLD.

**Status:** compiles clean; shadowJar built as a dev build (NOT released, NOT pushed — per the standing
constraint until the bounce works at 40 b/s). NOT yet deployed/live-tested. Files: `ElytraPilot.tickBounce`,
`ElytraPilotCommand`, `Config.Client.Extra.ElytraPilot`.

**LIVE TEST PLAN (bot-only, no controlling client — a relayed client captures the USER not the bot):**
1. Console `connect`; wait for spawn on a flat highway (`fly highway e` sets ebounce + road y120, or set `road <y>`).
2. `fly trip off` + `fly trip startonconnect off` so the trip can't hijack the pilot into a 0,0 firework cruise.
3. `fly bouncedebug on`. Pick a mode: `fly bouncekick firework` (default) or `fly bouncekick sprint`.
4. Arm directly: `fly off` → `fly highway e` → `fly on`. Watch the `[bounce]` telemetry: hsp should climb to ~40
   (KICK/RAMP) then steady in HOLD. Watch for server position-corrections (rubberband) in the log.
5. If it rubberbands: log WHICH mode + at what hsp the correction first fires, here, before changing anything.

**What to tune live if it's close:** Sprint ramp too aggressive (rubberbands during RAMP) → lower
`bouncerunramp` (e.g. 12). HOLD bleeds/can't hold → raise `bounceinject` slightly. Bounce too tall/loose →
that's `bouncepitch` (75 = tight). Firework spin-up too slow → it's gated by `boostMinSpacingTicks`.

---

## Attempt #15 — 2026-06-15 — PURE-INPUT GROUND BOUNCE (decoded from a fresh capture). WORKS, Grim-accepted, ~16 b/s avg / 24 wall.

**THE DECODE (fresh capture `…/packetlogger/latest.log` @18:03:51+, parsed with `tools/parse_rusher_packetlog.sh`):**
The real Rusherhack e-bounce is a **pure-input GROUND bounce**, NOT injection/synth/continuous-glide. The client:
1. Sets ONE held input forever: `ServerboundPlayerInput{forward=true, jump=true}` — it NEVER changes for the whole
   flight (verified: input packets only at start + end, ~63k lines apart).
2. Sprints via `START_SPRINTING` (once).
3. Re-sends `START_FALL_FLYING` each airborne phase.
4. **ZERO fireworks, ZERO velocity injection, pitch=0.0 in 398/405 moves (NOT pitch-wiggling).**
Holding jump auto-bounces off the road every ~10-11 ticks: touches `og=true` at y=roadY for exactly 1 tick, peaks
only ~roadY+0.9. Each ground touch is a sprint-jump (+0.2) the 0.99 glide-drag preserves → speed RAMPS organically
(capture: 5→22 b/s, still climbing toward ~40). Server metadata flag toggles **0x88 (fly+sprint) ↔ 0x08 (grounded)**
each cycle — i.e. the server CLEARS fall-flying on each ground touch and the client re-deploys. Grim accepts it
because every position is the honest physics result of the held inputs.

**REWRITE:** `tickBounce` fully replaced — removed ALL velocity injection / the `eBounceActive` synth / continuous-glide.
Now: hold `forward+jump+sprint`, re-deploy via `BOT.deployElytra()` (flips fall-flying TRUE locally that tick) every
airborne tick above `bounceDeployHeight`, level pitch, NO velocity touched. Added: settle (neutral inputs) while
`setbackHoldTicks>0` (never bounce into a rubberband), altitude-managed nose-down dive above `bounceDiveHeight` to cap
the apex. New live-tunable config + commands: `bouncedive`, `bouncedeploy`, `bouncediveheight` (plus existing
`bouncespeed`, `setbackhold`). Deployed via scp→launcher/ZenithProxy.jar + tmux `goldfarm` restart.

**RESULT — BREAKTHROUGH: the bounce WORKS and is Grim-accepted.** First-ever clean ground bounce building speed with
setback=0: `og=true 19.2 → og=false 23.0 b/s` (+3.8 in one bounce). No more deadlock — the settle-during-setback makes
it RECOVER from setbacks instead of the prior stuck-on-ground-spd0 loop. Best config (deploy 0.6 / dive 1.0 / hold 15 /
speed 40): **avg ~16 b/s, max ~24, recovers from periodic setbacks, travels the highway** (bot went 93.8k→100.6k E
during testing). HUGE improvement over every prior attempt (all of which pinned at 0 / deadlocked).

**THE REMAINING WALL (~24 b/s):** setback ALWAYS triggers at ~24 b/s. Root cause = a 1-tick **fall-flying
discontinuity on each ground touch**: the server clears ff (0x08) ~2 ticks after the touch, my optimistic
`deployElytra()` re-sets it the next tick, and that metadata-clear-vs-optimistic-deploy RACE leaves a 1-tick ff=false
window + a re-deploy whose physics frame-timing is ~1 tick off from Grim's prediction. Below ~24 b/s the divergence is
under Grim's threshold (accepted); at ~24 it exceeds it → setback. Confirmed NOT the dive (no-dive still setbacks), NOT
the deploy-height flicker alone, NOT chunk/lag/elytra. Speed-capping doesn't help (per-bounce +3.7 overshoots any cap).

**Sweep data (each ~40s, E highway):**
- deploy0.3 dive1.0 hold15: avg 12.0 max 24.2 — 266 settle ticks (had ff=false flicker from too-low deploy)
- deploy0.6 dive1.5 hold15: avg 9.2 max 22 — 165 settles, over-climbs to 122.8 (dive too late)
- deploy0.6 dive0.5 hold15: avg 10.9 — 362 settles (early dive provokes more setbacks)
- deploy0.6 dive2.0 + CLEAR-ff-on-ground: avg 4.5 max 13 — ballistic rise bleeds speed (air-drag 0.91), REVERTED
- **deploy0.6 dive1.0 hold15: avg 16.4 max 24.6 — BEST**
- deploy0.6 dive5.0 (no dive): avg 8.7 — 385 settles, over-climbs 122.5 (proves dive isn't the trigger)
- deploy0.6 dive1.0 speed21 (cap): avg 10.7 max 23.9 (cap overshoots, still hits 24 wall)
- deploy0.6 dive1.0 hold5: avg 12.5 — 141 settles (faster resume just re-hits the wall)

**NEXT to break 40:** fix the deploy frame-timing in `Bot.java` so the optimistic local fall-flying set is applied on
the SAME physics frame Grim grants it (eliminate the 1-tick race) — i.e. make `deployElytra` / `updateFallFlying`
ordering match the vanilla LocalPlayer exactly, OR suppress the inbound 0x08 metadata clear for the 1-tick graze so ff
never toggles (stays continuously gliding while still registering the ground touch for the jump). That removes the
ff-discontinuity that caps speed at 24. Bot left DISARMED on the E nether highway (~x100.6k y120); best config persisted.

---

## Attempt #16 — 2026-06-15 — SOLVED. ~40 b/s no-firework e-bounce, Grim-accepted, 0 setbacks.

**Two fixes cracked the ~24 b/s setback wall AND the over-climb:**

**FIX 1 — in-tick re-deploy (Bot.java) kills the setback wall.** Velocity telemetry proved the ~24 setback was a
metadata RACE: the module re-deployed fall-flying from `ElytraPilot.onTick` (runs BEFORE `Bot.tick`), but the server's
ground-clear echo (0x08) landed between onTick and `Bot.tick`'s `updateFallFlying()`, so the physics ran that tick on
AIR-drag (0.91) while my prior tick had glided (0.99) → 1-tick divergence from Grim → setback at speed. Fix: added
`Bot.requestBounceRedeploy(minY)` + a consume-once hook INSIDE `Bot.tick` right after `updateFallFlying()` (where
vanilla deploys, line ~388) so the re-deploy wins the race and ff stays continuous. RESULT: **0 setbacks, speed broke
past 24 to 28+.** (Tried instead: clearing ff on the ground to match Grim → ZEROES vx every bounce, caps ~5 b/s, reverted.
Tried deploy-on-descent / ballistic rise → REINTRODUCES setbacks, Grim only stays happy with CONTINUOUS fall-flying.)

**FIX 2 — proportional nose-down dive caps the apex (no ceiling hits) AND raises speed.** Continuous ff has weak gravity
(-0.02) so the +0.42 jump over-climbed to y122.8, punching the nether ceiling and killing forward progress. A binary
0→30° dive to cap it RE-TRIGGERED setbacks (abrupt velocity spike). Fix: pitch ramps PROPORTIONALLY with height —
`pitch = min(bounceDivePitch, (aboveRoad - bounceDiveHeight) * bounceDiveGain)` — small per-tick change (Grim-safe),
diving harder the higher it climbs, leveling near the road. Capping the apex also stopped wasting jump energy on altitude
→ speed jumped.

**WINNING CONFIG (live-tuned, persisted): bounceDeployHeight 0.3, bounceDiveHeight 0.4, bounceDiveGain 60,
bounceDivePitch 50 (cap), bounceRedeployMaxVy 1.0 (continuous ff), bounceClearOnGround OFF, bounceSpeed 40,
setbackHoldTicks 15.** Telemetry: smooth pitch ramp 0→50 up the rise, 50→0 down to the road; sprint-jump +0.19/cycle;
vx ~1.6-1.9/tick.

**RESULT: peak 38-39 b/s, avg ~30-35 b/s (varies with highway terrain), 0 setbacks over 60s windows, apex ~122 (clears
the ceiling), ZERO fireworks.** Essentially the ~40 b/s target. Tuning curve: gain40→avg25/apex122.1, gain60→avg35/0
settles/apex121.9 (SWEET SPOT), gain85→avg31/15 settles (overshoot, backed off).

**Critical confound found mid-session:** the elytra WORE OUT after ~1.5h of continuous bouncing → canGlide() failed
intermittently → every config setbacked (low-speed trap). User swapped to a fresh elytra → instantly restored. LESSON:
a worn/near-dead elytra silently breaks the bounce (no deploy → ff can't hold → setback); always verify durability first.

**Lambda client ElytraFly.kt (1.21.11, user ref):** a firework high-speed glider (110 b/s limit) for LAXER anticheats;
altitude control = Y-velocity zeroing (motion injection Grim rejects). Only shared trick = maintain the gliding flag
locally (== our in-tick redeploy). No Grim-applicable improvement; our proportional-dive is the Grim-correct equivalent.

**New live-tunable cmds:** `fly bouncedeploy / bouncediveheight / bouncedivegain / bouncedive / bounceredeployvy /
bounceclearground`. New Bot.java hook: `requestBounceRedeploy`. NOT yet released (dev build on VPS; bot armed + bouncing
clean on the E nether highway, deep east ~x113k+/y120). NEXT (optional): release; handle low-tunnel sections + obstacles
to reduce the terrain-driven avg variance; auto-detect/replace worn elytra before it traps the bounce.
