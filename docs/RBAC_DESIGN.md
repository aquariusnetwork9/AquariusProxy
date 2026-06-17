# AquariusProxy RBAC — design doc (for review, not yet implemented)

## Why

Today access control is three disconnected things:

- **Binary command auth** — `PlayerCommandSource.validateAccountOwner()` returns true only if you're logged in
  as the proxy's own MC account, or (optional flag `allowWhitelistedToUseAccountOwnerCommands`) if you're on the
  whitelist. There's no middle ground: you either run *everything* or *nothing*.
- **Whitelist/PlayerLists** — `PlayerListsManager` keeps `whitelist`, `blacklist`, `spectatorWhitelist`, … as flat
  UUID lists; `kickNonWhitelistedPlayers()` gates login. Pure allow/deny, no capabilities.
- **A separate PearlPlus whitelist** (`CONFIG.client.extra.pearlPlus.whitelist`) for who may whisper `load`.

We want one **role-based** system that **replaces the whitelist** and gates features granularly: per-user role
assignment + per-user API tokens, with **admin / operator / user / guest** roles controlling which
modules, movement/actions, and commands each subject may use. The same system authorizes the new HTTP command
API (and therefore the ProxyBridge whisper-intercept / mute-bypass and remote pearl pull).

## Concepts

- **Subject** — a thing making a request: a connected player (by UUID), or an API caller (by token). Resolves to a
  **role** plus optional per-subject permission overrides.
- **Role** — a named bundle of permissions. Ordered by privilege: `ADMIN > OPERATOR > USER > GUEST`. Plus the
  implicit `NONE` (unknown subject). Roles are **hierarchical**: a role inherits every lower role's permissions
  (`admin ⊇ operator ⊇ user ⊇ guest`), so each role's config lists only what it <i>adds</i> over the one below.
  (Note: only roles inherit; capability <b>groups</b> stay flat.)
- **Permission** — a capability string, wildcard-aware. Namespaces:
  - `command.<category>` — a whole command category: `command.core`, `command.info`, `command.manage`, `command.module`
  - `command.<name>` — one command by name (fine-grained grant/deny, e.g. `command.pearlplus`)
  - `module.<name>` / `module.*` — toggle/use a module
  - `action.move`, `action.chat`, `action.interact`, `action.respawn` — in-world actions (enforced via ActionLimiter)
  - `connect.spectate` — may join as a spectator; `connect.control` — may take the controlling-player slot
  - `pearl.pull` — load your own registered pearl; `pearl.manage` — add/remove/admin pearls
  - `group.<name>` — a **capability preset** (see below) that expands to many of the above (e.g. `group.combat`)
  - `*` — everything
- **Capability presets (groups)** sit between roles and raw permissions so admins assign intent, not internals:
  `group.combat`, `group.crafting`, `group.movement`, `group.travel`, `group.automation`, … Each group is a
  config-defined bundle of `module.*`/`command.*`/`action.*` perms. Roles and per-user `grants` reference groups by
  name; the resolver expands them. There are ~45 modules with different triggers — groups keep assignment sane.
- A subject's effective permission set = `expand(role.permissions ∪ subject.grants) \ subject.denies`, where
  `expand` recursively resolves `group.<name>`. Checks support wildcards (`module.*` grants `module.killaura`);
  explicit `denies` always win.

### Default roles (all overridable in config)

| Role     | Grants |
|----------|--------|
| admin    | `*` (unrestricted) |
| operator | `connect.control`, `command.info`, `command.module`, `action.*`, `pearl.*`, and **all preset groups** (every non-admin module) — i.e. `module.*` minus the admin-exclusive set |
| user     | `connect.control`, `pearl.pull`, `action.move`, `action.chat`, plus assigned `group.<name>` / `module.<name>` |
| guest    | `pearl.pull` only — **API/pearl access, no in-game presence** (no spectate by default) |
| none     | **nothing — no connection, no interaction** (see below) |

The proxy's own MC account is **always** ADMIN and can never be locked out (fail-safe).

**Default-deny is the whole posture.** A subject with no explicit assignment is `none`: it cannot connect (play or
spectate) and cannot touch the API. There is no anonymous self-service — e.g. a random player who drops a pearl into
a public chamber must be added (by an admin/operator) before they can pull it later. This is the intentional
replacement for the old whitelist + spectator-whitelist, which were allow/deny-only and too coarse for modern
multi-bot base operations.

## Data model / config

Replace the whitelist config with a `permissions` block (proposed `CONFIG.server.permissions`):

```jsonc
"permissions": {
  "enabled": true,
  "defaultRole": "none",          // unknown subject => deny everything (the chosen posture). Not configurable to "guest".
  "minConnectRole": "guest",      // must resolve to >= guest (i.e. be explicitly assigned) to connect at all
  "groups": {                     // capability presets -> permission list (config-defined, granular)
    "movement":   ["action.move","action.interact","command.goto","command.pathfinder","module.boat"],
    "travel":     ["module.elytrapilot","module.elytratrip"],          // ebounce + nether flight (long-haul)
    "combat":     ["module.killaura","module.autototem","module.autoeat","module.automend","module.autoarmor",
                   "module.autoomen","module.spook","module.autorespawn","module.spawnpatrol","module.basepatrol"],
    "crafting":   ["module.villagertrader","module.pearldrop","module.aquariusminer","module.aquariussniffer",
                   "module.kitmaker","module.enchanter","module.stashscanner","module.orderfiller","module.regear"],
    "automation": ["module.antiafk","module.autofish","module.autodrop","module.tasks"],
    "chat":       ["module.autoreply","module.extrachat","module.chathistory","module.click"],
    "utility":    ["module.antikick","module.antileak","module.autodisconnect","module.sessiontimelimit",
                   "module.activehours","module.autoomen","module.requeue","module.queuewarning"],   // operator+
    "system":     ["module.bridge","module.autodetectmodule","module.autoloadmodule","module.autoreconnect"] // operator+
  },
  "roles": {                      // INCREMENTAL perms; each role inherits every lower role (admin>operator>user>guest)
    "guest":    ["pearl.pull"],
    "user":     ["connect.control","action.move","action.chat"],      // + inherits guest
    "operator": ["command.info","command.module","action.*","pearl.*",
                 "group.movement","group.travel","group.combat","group.crafting","group.automation",
                 "group.chat","group.utility","group.system"],          // + inherits user/guest
    "admin":    ["*"]
  },
  "users": {                      // UUID -> assignment
    "<uuid>": {
      "name": "Player",
      "role": "user",
      "grants": ["group.combat","group.travel"],          // assign presets, not internals
      "denies": ["module.spammer"],                       // deny always wins
      "tokens": ["<sha256 of token>"],                    // hashed; plaintext shown once on issue
      "pearlScope": "self",                               // self | none — whose pearls this token may pull
      "connectMode": "control"                            // control | spectate — how this user joins (see Join-as-spectator)
    }
  }
}
```

Tokens are stored **hashed** (SHA-256); the plaintext is shown once at issue time. A user may hold several tokens
(rotate/revoke individually).

**Admin-exclusive (never in a preset, admin role only):** the sensitive/owner surface — `coordobfuscation`,
`spammer`, `replaymod`, `visualrange`, all **ActionLimiter** restriction controls, all **config-changing /
`command.manage`** commands, and the **permission system itself** (user/role/group/key assignment, the `/perms`
API + panels). These require `*`/admin and are excluded from `module.*` for `operator`. (Mapped from the
ZenithProxy wiki's sensitive-feature set.) `combat` includes a future **`basepatrol`** module (placeholder until it
ships).

## Enforcement points (where it wires in)

1. **Command authorization** — the heart. Generalize `CommandSource`:
   - Replace the boolean `validateAccountOwner(ctx)` usage with a permission check
     `Permissions.allows(subject, command)` resolved from the source's subject. Keep `validateAccountOwner` as a
     thin shim (`allows(subject, "command.core")` / owner check) so existing call sites keep working during
     migration.
   - `PlayerCommandSource` resolves the subject from the connected player's UUID.
   - New `ApiCommandSource` resolves the subject from the request token.
   - A command's required permission derives from its `CommandUsage` category by default
     (`command.<category>`), with optional per-command override (`command.<name>`). `Command` gains an optional
     `requiredPermission()` (default = category-derived).
2. **Login gating** — replace `kickNonWhitelistedPlayers()` with a permission check: deny the connection unless the
   subject has `connect.spectate` (to spectate) or `connect.control` (to take the controlling slot). Unknown subjects
   (`none`) are dropped at login. The whitelist, `whitelistEnabled`, and the **spectator whitelist** are all
   superseded by `users` + per-role `connect.*`.
3. **Module gating** — when a command enables/uses a module, check `module.<name>`. Centralize in the
   module-toggle path so every `/<module> on` is gated uniformly.
4. **Movement / action gating** — drive **ActionLimiter** from the connected player's role:
   a player missing `action.move`/`action.chat`/`action.interact`/`action.respawn` is run under ActionLimiter
   restrictions automatically (guest = no movement/chat; user = movement+chat). This reuses the existing,
   proven restriction primitive instead of inventing a new one.
5. **Pearl functions** — `pearlplus load` requires `pearl.pull` and (for others' pearls) `pearl.manage`; the
   per-token `pearlScope` limits a guest/user token to pulling only *their own* registered pearl.
6. **HTTP command API** (the prerequisite for remote pull + whisper-intercept):
   - Small **netty** HTTP server (the proxy already bundles `netty-codec-http`), config-gated, **bound to
     `127.0.0.1` by default** with explicit opt-in (`bindHost`) for remote exposure.
   - `POST /command` `{ "command": "..." }` with `Authorization: <token>` → resolve token → `ApiCommandSource`
     with that subject's role → run through `CommandManager.execute` → return
     `{ embed, embedComponent, multiLineOutput }` (the shape ProxyBridge/ZenithProxyMod already speak).
   - Per-token rate limiting; constant-time token compare; structured audit log of every API command + subject.

## Client side (ProxyBridge)

- **Whisper-intercept / mute-bypass** — `ClientSendMessageEvents.ALLOW_COMMAND`/`ALLOW_CHAT` cancel an outgoing
  `/msg|/w|/tell|/whisper <name> <text>` when `<name>` is a registered bot, and reroute `<text>` (mapped to the
  bot's command, e.g. `load` → `pearlplus load <self> <id>`) over that bot's HTTP API with the user's token. No
  role logic lives on the client — the **server** enforces; the client just gets "denied" if the token's role
  lacks the permission.
- Reuses the bot list + WebAPI client already shipped (`/pb bots`, `/pb pull`).
- **Join as spectator (no bot interruption)** — normally connecting takes the controlling-player slot and interrupts
  whatever the bot is doing; you then `/swap` to spectator. For subjects with `connect.spectate`, ProxyBridge adds a
  **"Join as spectator" button** on the multiplayer server-list entry for a known proxy (the same mixin hook the base
  ZenithProxyMod uses for its join sprite). It sets the user's `connectMode = spectate` on the bot via the HTTP API
  (your token) *before* connecting, so the proxy drops the connection straight into spectator mode and the bot keeps
  running. A matching "Join as controller" clears it (requires `connect.control`). The proxy honors
  `users.<uuid>.connectMode` at login; guests (spectate-only) always land in spectator regardless. This replaces the
  interrupt-then-`/swap` dance.

## Admin panels (so you don't need the CLI)

Managing users/roles/groups/tokens by hand-editing config or typing commands is fine for power users but a barrier
for everyone else. Two front-ends over the same admin API, the proxy staying the single source of truth:

**Admin API** — a small authenticated surface on the HTTP server (admin role required), beyond `/command`:

- `GET /perms` — snapshot: roles, groups, users (names + roles, **never** token plaintext/hashes), API bind info.
- `POST /perms/users` — add / assign role / set grants+denies / **bulk** assign-unassign (accepts a list).
- `DELETE /perms/users/<uuid>` — remove.
- `POST /perms/tokens` — issue a token for a user (returns plaintext **once**); `DELETE /perms/tokens/<id>` — revoke.
- `GET /perms/groups`, `POST /perms/groups` — view/edit preset bundles.

(Mutations could also just be `perms …` commands through `/command`; the structured GET endpoints exist so a panel
can *list* current state. Either way the panel never holds config — it round-trips the bot.)

**ProxyBridge mod GUI (primary, fuller panel)** — an in-game `Screen` (e.g. `/pb admin` or a button on the bot
list). Talks to the selected bot's admin API with your admin token. Shows a user table (name · role · groups) with:
add by username (resolves UUID), assign/unassign a role, toggle capability presets per user (checkboxes for
combat/crafting/movement/travel/automation/…), multi-select **bulk** assign/unassign, issue/revoke tokens, and an
"API info" tab (bind address, your role, endpoint list, per-bot token entry). Minecraft's GUI toolkit handles the
tables/checkboxes the CLI can't.

**Discord panel (simpler, constrained)** — reuses the existing `Panels` + button/modal/select-menu infra
(cf. `DISCORD.openPanel(Panels.PEARLDROP)`). Discord can't do rich tables, so: a user **select menu** → role
**select menu** + preset **multi-select**, an "Add user" modal (username), and "Issue token" (DMs the plaintext).
Bulk ops via multi-select where Discord allows. Same admin API underneath.

Both panels are admin-gated by the same permission system — only `command.manage`/admin subjects can open them.

## Migration & safety

- **Owner is always admin** — resolved before config; a broken/empty `permissions` block leaves only the owner
  with access (fail-safe, never a lockout).
- **Whitelist import** — one-time: existing `whitelist` UUIDs → `users` with role `user` (configurable target
  role); `blacklist` → denied; `spectatorWhitelist` → `guest`/spectate cap. Old whitelist fields kept read-only
  for one release, then removed.
- **Legacy mode** — `permissions.enabled=false` falls back to today's whitelist behavior, so the change can ship
  dark and be toggled on.
- **Audit** — every allow/deny at an enforcement point is logged with subject + permission for debugging and
  abuse review.

## Phased implementation

1. **Core model** — `Permission`, `Role`, `Group`, `Subject`, `Permissions` resolver (with group expansion) +
   config schema + unit tests (resolution, group expansion, wildcards, deny-over-grant, owner-always-admin). No
   behavior change yet.
2. **Command auth** — route `validateAccountOwner`/category checks through `Permissions`; add `requiredPermission()`
   to `Command`; keep legacy mode.
3. **Login + module + action gating** — replace `kickNonWhitelistedPlayers`; gate module toggles; map roles →
   ActionLimiter. _(Done: RBAC login gating in `SLoginFinishedOutgoingHandler` honoring `connect.*` + `connectMode`;
   central MODULE-command gate in `CommandManager` = `module.<name>` or `command.module`. Remaining: the role →
   ActionLimiter movement/chat mapping.)_
4. **HTTP API** — netty server + `ApiCommandSource` + `/command` + the `/perms` admin surface + token issue/revoke
   (`/perms token …`), localhost-bound, audited.
5. **Admin panels** — Discord panel first (reuses existing infra), then the ProxyBridge mod GUI screen.
6. **ProxyBridge whisper-intercept** — client reroute over the API.
7. **Whitelist migration + removal** — importer, then delete the old fields.

## Decisions (locked)

- **Default-deny.** Unknown subject = `none` = no connection, no spectate, no API, no pearl pull. Explicit
  assignment is required for *any* interaction. Not configurable to auto-guest. Private bot, not a public service.
- **Replaces** the whitelist, `whitelistEnabled`, the spectator whitelist, and the PearlPlus whitelist outright.
- **Connection** is itself a permission: `connect.control` (take the player) and `connect.spectate` (spectate).
  Guest spectates; user/operator/admin control.

## Proposed defaults (adjust before Phase 1)

- **Config location** — `CONFIG.server.permissions` (server-scoped; replaces `server.extra.whitelist`).
- **Operator's `command.manage` subset** — info/diagnostic + connection management, e.g. `command.reconnect`,
  `command.kick`, `command.spectator`, `command.stats`; **not** account/auth/config-secret commands (admin-only).
- **Pearl scope** — per-token `pearlScope: self` by default (you can only pull *your own* registered pearl);
  `pearl.manage` (operator+) can pull/manage on behalf of others.
- **Tokens** — `Authorization` header; hashed at rest; multiple per user; optional TTL/expiry field (off by default).

## More decisions (from review)

- **Guest = pearl/API only**, no in-game presence (no `connect.spectate` by default).
- **Capability presets** (`group.combat|crafting|movement|travel|automation|…`) abstract the ~45 modules; each is
  granular in config, and per-user `grants` assign presets rather than individual modules.
- **Admin panels** are first-class: a fuller **ProxyBridge mod GUI** + a simpler **Discord panel**, both over the
  same admin API (add/delete/assign/unassign/bulk + token/API info).

## Settled (this review)

- **Hierarchical roles** — `admin ⊇ operator ⊇ user ⊇ guest`; each role's config lists only its increment over the
  role below (an operator automatically does everything users and guests can).
- **Flat presets** — `travel` does not auto-imply `movement`; grant both if you want both. (Roles inherit; groups don't.)
- **`chat`** preset (`autoreply`/`extrachat`/`chathistory`/`click`), **`utility`** (the safety/connection autos),
  and **`system`** (`bridge`/`autodetect`/`autoload`/`autoreconnect`) are all **operator-default** groups (granted to
  operator+; assignable to a user only by explicit grant). New ungrouped modules default to **admin-only** (safe).
- **Join-as-spectator** button is in scope (`connect.spectate`, `connectMode` preference) — see Client side.

## Branches

- **AquariusProxy** side lives on the **`v5.0.0-dev`** branch (this is a major release — it replaces the whitelist).
- **ProxyBridge** (the mod) lives on `main` in its own repo.
