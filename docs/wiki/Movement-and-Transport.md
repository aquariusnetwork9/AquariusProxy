# Movement & Transport

AquariusProxy moves the bot around with a server-side port of the **Baritone pathfinder**, plus two AquariusProxy-specific transport integrations: **bubble-column traversal** and **boat autopilot**. For air travel see the [ElytraPilot](Commands.md#elytrapilot) module.

The full, auto-generated command list lives in the [Commands reference](Commands.md). This page covers the base movement commands and the two integrations in more detail.

## Pathfinder (Baritone)

Command: `pathfinder` — aliases `path`, `b`. (Discord `.b`, in-game `/b` or `!b`.)

The pathfinder routes the bot to a goal, mines/breaks/places blocks, follows players, and picks up drops. Common commands:

```
b goto <x> <z>            walk to coordinates (XZ)
b goto <x> <y> <z>        walk to an exact block
b goto <waypointId>       walk to a saved waypoint
b follow [<player>]       follow a player
b thisway <blocks>        walk forward N blocks
b mine <block>            strip-mine for a block type
b getTo <block>           path to the nearest block of a type
b clearArea <pos1> <pos2> clear a cuboid
b stop                    cancel the current path
```

See the [Commands reference](Commands.md#pathfinder) for the complete list (click, break, place, near, pickup, status, settings).

## Bubble-column traversal

!!! note "New in AquariusProxy 5.0 — ✅ live-validated (exit-timing polish pending)"

    The pathfinder can now **enter, ride, and exit bubble columns** as first-class movements — both **soul-sand columns (up)** and **magma columns (down)**. This is automatic: when a bubble column lies on the route, the planner uses it like any other move; no special command is required, just `b goto`/`b mine`/etc. as usual.

How it works:

* Soul-sand bubble columns push the bot **up**; magma columns pull it **down**. The pathfinder treats `BUBBLE_UP` / `BUBBLE_DOWN` as cheaper-than-walking moves, so it prefers a column when one is available, and exits laterally onto the first walkable floor.
* The bot enters/exits the water column and rides the bubbles using the proxy's built-in column physics; the planner only needs a **solid, standable block** at the top as the goal (you can't path *into* the column itself — the bubbles push you off).

!!! tip "Diagnosing a column that won't path"

    `b bubbledebug <x> <y> <z>` dumps the pathfinder's per-block view of a column shaft (passability, `isBubbleColumnUp`, walkable-floor) to the console log — grep `[bubbledebug]`. Useful when a column is fed by water held in place with **signs** (the bot must be able to path through the signs to reach the column).

!!! warning "Known rough edge"

    The lateral **exit can overshoot** slightly before the bot steps off the top of the column. It works, but the exit timing is a polish item.

## Boat control

Command: `boat`. (Discord `.boat`, in-game `/boat` or `!boat`.)

!!! note "New in AquariusProxy 5.0 — ✅ live-validated"

    AquariusProxy reimplements Minecraft's `AbstractBoat` physics server-side, so the bot can **drive a boat across open water to a coordinate**. The bot becomes the controlling client and reports its motion via the MoveVehicle packet.

```
boat mount            right-click the nearest empty boat to seat the bot, then arm control
boat goto <x> <z>     autopilot: steer across open water to the coordinate, then stop
boat on / off         enable / disable boat control
boat fwd / back       manual: hold forward / reverse
boat left / right     manual: hold turn
boat fwdleft / fwdright   manual: forward while turning
boat stop             release manual input / cancel goto (stays enabled)
```

Typical use: get within ~8 blocks of a boat, `boat mount`, then `boat goto <x> <z>`.

!!! warning "Open water only"

    The autopilot is designed for **open water**. It steers toward the target and stops within a few blocks of it; it does not currently route around islands or land obstacles. Seat the bot in a boat first (`boat mount`, or have a controlling player right-click a boat).
