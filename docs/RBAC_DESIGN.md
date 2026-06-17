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
  implicit `NONE` (unknown subject).
- **Permission** — a capability string, wildcard-aware. Namespaces:
  - `command.<category>` — a whole command category: `command.core`, `command.info`, `command.manage`, `command.module`
  - `command.<name>` — one command by name (fine-grained grant/deny, e.g. `command.pearlplus`)
  - `module.<name>` / `module.*` — toggle/use a module
  - `action.move`, `action.chat`, `action.interact`, `action.respawn` — in-world actions (enforced via ActionLimiter)
  - `connect.spectate` — may join as a spectator; `connect.control` — may take the controlling-player slot
  - `pearl.pull` — load your own registered pearl; `pearl.manage` — add/remove/admin pearls
  - `*` — everything
- A subject's effective permission set = `role.permissions ∪ subject.grants \ subject.denies`. Checks support
  wildcards (`module.*` grants `module.killaura`); explicit `denies` win over grants.

### Default roles (all overridable in config)

| Role     | Grants |
|----------|--------|
| admin    | `*` (unrestricted) |
| operator | `connect.control`, `command.info`, `command.module`, `module.*`, `action.*`, `pearl.*`, plus selected `command.manage` items |
| user     | `connect.control`, `pearl.pull`, `action.move`, `action.chat`, and an assigned set of `module.<name>` / `command.<name>` |
| guest    | `connect.spectate`, `pearl.pull` |
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
  "roles": {                      // role -> permission list (defaults shipped, user-editable)
    "admin":    ["*"],
    "operator": ["command.info","command.module","module.*","action.*","pearl.*"],
    "user":     ["pearl.pull","action.move","action.chat"],
    "guest":    ["pearl.pull"]
  },
  "users": {                      // UUID -> assignment
    "<uuid>": {
      "name": "Player",
      "role": "user",
      "grants": ["module.autoeat","command.pearldrop"],   // per-user additions
      "denies": [],
      "tokens": ["<sha256 of token>"],                    // hashed; plaintext shown once on issue
      "pearlScope": "self"                                // self | none — whose pearls this token may pull
    }
  }
}
```

Tokens are stored **hashed** (SHA-256); the plaintext is shown once at issue time. A user may hold several tokens
(rotate/revoke individually).

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

1. **Core model** — `Permission`, `Role`, `Subject`, `Permissions` resolver + config schema + unit tests
   (resolution, wildcards, deny-over-grant, owner-always-admin). No behavior change yet.
2. **Command auth** — route `validateAccountOwner`/category checks through `Permissions`; add `requiredPermission()`
   to `Command`; keep legacy mode.
3. **Login + module + action gating** — replace `kickNonWhitelistedPlayers`; gate module toggles; map roles →
   ActionLimiter.
4. **HTTP command API** — netty server + `ApiCommandSource` + token issue/revoke commands (`/perms token …`),
   localhost-bound, audited.
5. **ProxyBridge whisper-intercept** — client reroute over the API.
6. **Whitelist migration + removal** — importer, then delete the old fields.

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

## Still open

- Exact module sets for a default **user** (probably empty until assigned) vs **operator** (`module.*` minus a few
  dangerous ones like `coordobfuscation`/`spammer`?).
- Whether **guest** should get `connect.spectate` at all, or be API/pearl-only with no in-game presence.
