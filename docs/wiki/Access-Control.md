# Access Control (RBAC)

Access control replaces the old whitelist with **roles** and **per-user permissions**: each player gets a role
(`guest` / `user` / `operator` / `admin`), and roles unlock movement, modules, pearl pulls, and a remote API. You can
also grant individual players extra abilities or take some away.

It ships **off**. Until you turn it on, the proxy behaves exactly as before (the old whitelist still applies). The
**account owner is always an admin** — automatically, with no token — so you can never lock *yourself* out.

!!! info "Command prefixes"

    Examples below use the **terminal** form (no prefix). In **Discord** prefix every command with `.`
    (e.g. `.perms status`); **in-game** prefix with `/` or `!` (e.g. `/perms status`).

---

## Setup

### Owner — turn it on (once)

Run these three commands in order:

```
perms migrate apply
perms status
perms enable on
```

1. **`perms migrate apply`** imports your existing lists: everyone on the **whitelist** becomes a `user` (can connect
   and control the bot); everyone on the **spectator** list becomes a `guest` who may join as a spectator. (Run
   without `apply` first — `perms migrate` — to preview the changes.)
2. **`perms status`** shows what got imported so you can sanity-check it.
3. **`perms enable on`** flips access control on — it now governs who can connect and what they can do. You stay admin.

!!! warning "Default-deny — migrate first"

    The moment you `perms enable on`, **anyone who isn't assigned a role can't connect at all.** That's why you run
    `perms migrate apply` *first* — so your existing players keep their access. You (the owner) are always admin and
    are never affected.

???+ tip "Undo / changed your mind"

    `perms enable off` instantly reverts to the old whitelist behavior. Nothing you set up is lost — turning it back
    on restores it.

### Admin — add & manage people

```
perms user add <name> <role>          # e.g. perms user add Steve user
perms user role <name> <role>         # change someone's role later
perms user grant <name> group.<preset>  # give one extra ability group (see the table below)
perms token issue <name>              # mint an API token (shown once — copy it now)
```

Roles are `guest`, `user`, `operator`, `admin`. You don't need tokens for normal players — tokens are only for the
**remote API / mod GUI** (see below).

???+ tip "Prefer clicking to typing? Use the Discord panel"

    Run `perms panel` to post an interactive Discord panel: toggle access control + the API, add or bulk-assign
    users, set roles, tick capability presets on/off, issue/revoke tokens, and remove people — all with buttons.

### Tell your users

Hand these to your players:

* **To get access:** "Ask the bot owner/admin to add you. Until you're added, the bot won't respond to you."
* **To pull your pearl:** once you're added, whisper the bot in-game — `/msg <botname> load` (or `load <id>` if you
  have several). 
* **If you're chat-muted:** the pull still works if you run the **ProxyBridge** mod with the bot registered — your
  whisper is rerouted automatically (see *Companion mod* below).

---

## Manage / configure

Everything is driven by the `perms` command (admin-only) or the Discord panel — you never edit config by hand.

| Task | Command |
| --- | --- |
| Turn access control on/off | `perms enable on` / `perms enable off` |
| Turn the HTTP API on/off | `perms api on` / `perms api off` |
| Show current state | `perms status` |
| List / add / change / remove users | `perms user list` · `perms user add <name> <role>` · `perms user role <name> <role>` · `perms user remove <name>` |
| Give / take an ability | `perms user grant <name> <perm>` · `perms user ungrant <name> <perm>` |
| Block an ability (overrides a grant) | `perms user deny <name> <perm>` · `perms user undeny <name> <perm>` |
| How a user joins | `perms user mode <name> control` / `spectate` |
| Issue / revoke a token | `perms token issue <name>` · `perms token revoke <name> <index>` |
| Inspect one user | `perms user info <name>` |
| List roles / presets | `perms role list` · `perms group list` |
| Post the Discord panel | `perms panel` |

The full settings live in `config.json` under `server.permissions`, but you should manage them with the commands or
panel above rather than editing the file.

---

## Roles & presets

**Roles are hierarchical** — each includes everything below it:

| Role | Can do |
| --- | --- |
| **guest** | Pull their own pearl and use the API only. (No movement, no modules, no spectate by default.) |
| **user** | Connect and control the bot, move, and chat — plus any presets you grant them. |
| **operator** | Everything in every preset, plus info/module commands and pearl management. |
| **admin** | Everything, including the admin-only controls below and managing access control itself. |

**Capability presets** bundle related modules so you can grant abilities in one go — `perms user grant <name>
group.<preset>`:

| Preset | Unlocks |
| --- | --- |
| `group.movement` | Move, interact, Baritone `pathfinder`, boat autopilot |
| `group.travel` | ElytraPilot (elytra flight / nether travel) |
| `group.combat` | KillAura, AutoTotem, AutoEat, AutoMend, AutoArmor, AutoOmen, Spook, AutoRespawn, SpawnPatrol |
| `group.crafting` | VillagerTrader, PearlDrop, AquariusMiner, KitMaker, Enchanter, StashScanner, OrderFiller, Regear |
| `group.automation` | AntiAFK, AutoFish, AutoDrop, Tasks |
| `group.chat` | AutoReply, ExtraChat, ChatHistory, Click |
| `group.utility` | AntiKick, AntiLeak, AutoDisconnect, SessionTimeLimit, ActiveHours, Requeue, QueueWarning |
| `group.system` | Bridge, auto-detect/-load modules, AutoReconnect |

!!! note "Admin-only — in no preset"

    A few controls are intentionally **admin-exclusive** and aren't in any preset: coordinate obfuscation, Spammer,
    ReplayMod, VisualRange, the ActionLimiter, config/management commands, and access control itself. Grant them
    explicitly (e.g. `perms user grant <name> module.spammer`) only if you really mean to.

---

## How it stays dormant

While access control is off (`perms enable off`, the default), **every check falls back to the old behavior** — login
uses the old whitelist, commands use the old owner check, and no movement/chat is filtered. The HTTP API stays closed
(it needs *both* access control **and** `perms api on`). There is zero behavior change until you turn it on.

??? note "One thing that is always on"

    The ActionLimiter's blacklisted-item behavior changed independently of access control: a blacklisted item (e.g. an
    ender crystal) can now be **held but not used/placed**, instead of disconnecting you. This applies whenever you run
    ActionLimiter with an item blacklist, regardless of the access-control flag. To restore the old disconnect
    behavior, set `client.extra.actionLimiter.blacklistedItemDisconnect` to `true` in `config.json`.

---

## Companion mod — in-game GUI + muted whisper

The **[ProxyBridge](https://github.com/aquariusnetwork9/ProxyBridge)** Fabric client mod adds two access-control
conveniences on top of the proxy:

* **`/pb admin`** — an **in-game admin screen**: the user table, role and preset toggles, token issue/revoke, and the
  RBAC/API switches, all clickable. It talks to the bot over the HTTP API, so it needs an **admin token** for that bot
  (turn the API on with `perms api on`, then `perms token issue <you>` and add the bot in the mod with `/pb bots add`).
* **Muted-safe whisper** — `/w <botname> <command>` is rerouted over the API instead of in-game chat, so a chat-muted
  player can still drive the bot. Whichever role the token carries still governs what the command may do.

See the mod's README for install + the `/pb` commands.

---

## API & security hardening

The HTTP **command API** lets tokens run commands remotely (it's what the mod GUI and muted-whisper use). It is built
to be safe by default:

* **Off by default**, and bound to **localhost only** (`127.0.0.1:2480`) when on — not reachable from the internet.
* **A token is always required** (`403` otherwise). Tokens are stored only as salted hashes, compared in constant
  time, **rate-limited per token**, and every call is written to the audit log.
* Tokens are shown **once** at issue time and never again. Revoke with `perms token revoke <name> <index>`.

!!! warning "Exposing the API"

    To reach the API from another machine you must deliberately change `server.permissions.api.bindHost` (e.g. to
    `0.0.0.0`). Only do this behind a firewall/VPN or a reverse proxy with TLS — a reachable, token-guessable endpoint
    is a way into your bot. Localhost-only is the safe default; keep it unless you have a specific need.

---

## Troubleshooting

??? question "I enabled it and now players (or I) can't connect"

    Access control is default-deny — unassigned players can't join. You (the owner) are always admin, so connect and
    fix it: `perms migrate apply` to import the old lists, then `perms user add <name> <role>` for anyone missing. To
    bail out entirely, `perms enable off`.

??? question "A token isn't working over the API"

    Check that the API is on (`perms api on`) and that the token belongs to a user with the right role — `perms` and
    the admin screen need an **admin** token. Re-issue with `perms token issue <name>` if you lost the plaintext (it's
    only shown once).

??? question "A user can't use a module they should have"

    Grant the preset that contains it (`perms user grant <name> group.<preset>`) or the single module
    (`perms user grant <name> module.<name>`). Make sure they don't have a matching `deny` — a deny always wins
    (`perms user undeny <name> <perm>` to clear it). `perms user info <name>` shows their effective grants/denies.

??? question "How do I roll everything back?"

    `perms enable off` returns to the legacy whitelist instantly. Your roles/users stay saved for when you turn it
    back on.

---

[Full Commands Documentation](Commands.md){ .md-button .md-button--primary }

[Setup Documentation](Setup.md){ .md-button .md-button--primary }
