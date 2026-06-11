-- AquariusProxy stash system schema. Applied idempotently by StashDatabase.init() on startup
-- (CREATE ... IF NOT EXISTS / CREATE OR REPLACE), so no manual psql step is required. Lives in the
-- same Postgres database the proxy's stats databases use (databaseName "postgres", see ConnectionPool).
-- Mirrors stashBot CONTEXT.md section 5.

-- ---------- kit catalog (what a sellable kit IS) ----------
CREATE TABLE IF NOT EXISTS kit_types (
  id               SERIAL PRIMARY KEY,
  name             TEXT UNIQUE NOT NULL,             -- "pvp","obby","food" — used in Discord
  display_name     TEXT,
  color            TEXT,                             -- optional hint, nullable
  match_strictness TEXT NOT NULL DEFAULT 'item_and_count',
                                                     -- 'item_only'|'item_and_count'|'exact_components'
  signature        TEXT,                             -- canonical content hash (primary match key)
  notes            TEXT
);

CREATE TABLE IF NOT EXISTS kit_contents (            -- expected contents for template matching
  id          SERIAL PRIMARY KEY,
  kit_type_id INT NOT NULL REFERENCES kit_types(id) ON DELETE CASCADE,
  item_id     TEXT NOT NULL,                         -- "minecraft:obsidian"
  count       INT  NOT NULL,
  slot        INT
);

-- ---------- physical storage ----------
CREATE TABLE IF NOT EXISTS chests (
  id        SERIAL PRIMARY KEY,
  label     TEXT,
  dimension TEXT NOT NULL DEFAULT 'minecraft:overworld',
  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
  is_double BOOLEAN NOT NULL DEFAULT FALSE,
  role      TEXT NOT NULL DEFAULT 'storage',         -- 'storage'|'outgoing'|'supply'
  reachable BOOLEAN NOT NULL DEFAULT TRUE,           -- set FALSE if last scan couldn't reach it
  UNIQUE (dimension, x, y, z)
);

-- ---------- scan runs ----------
CREATE TABLE IF NOT EXISTS scans (
  id               SERIAL PRIMARY KEY,
  started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at      TIMESTAMPTZ,
  status           TEXT NOT NULL DEFAULT 'running',  -- 'running'|'complete'|'aborted'
  chests_scanned   INT NOT NULL DEFAULT 0,
  chests_unreached INT NOT NULL DEFAULT 0,
  shulkers_found   INT NOT NULL DEFAULT 0
);

-- ---------- shulker instances found in a scan ----------
CREATE TABLE IF NOT EXISTS shulkers (
  id                SERIAL PRIMARY KEY,
  scan_id           INT NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
  chest_id          INT NOT NULL REFERENCES chests(id),
  slot              INT NOT NULL,                    -- slot index in the chest window
  kit_type_id       INT REFERENCES kit_types(id),    -- NULL = unknown
  classification    TEXT NOT NULL DEFAULT 'unknown', -- 'matched'|'unknown'|'ambiguous'
  confidence        REAL,
  custom_name       TEXT,
  color             TEXT,
  content_hash      TEXT,
  contents          JSONB,                           -- [{item,count,slot}] for audit / relabel
  reserved          BOOLEAN NOT NULL DEFAULT FALSE,
  reserved_by_order INT,                             -- FK orders(id), nullable
  withdrawn         BOOLEAN NOT NULL DEFAULT FALSE,
  seen_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_shulkers_scan ON shulkers(scan_id);
CREATE INDEX IF NOT EXISTS idx_shulkers_kit  ON shulkers(kit_type_id) WHERE NOT withdrawn;

-- ---------- orders (payment-gated) ----------
CREATE TABLE IF NOT EXISTS orders (
  id                     SERIAL PRIMARY KEY,
  discord_user_id        TEXT NOT NULL,
  discord_channel_id     TEXT,
  status                 TEXT NOT NULL DEFAULT 'awaiting_payment',
                         -- 'awaiting_payment'|'paid'|'filling'|'ready'|'delivered'
                         -- |'failed'|'cancelled'|'expired'
  paid_at                TIMESTAMPTZ,
  payment_ref            TEXT,                        -- shop bot txn / ticket id
  reservation_expires_at TIMESTAMPTZ,                 -- soft-hold TTL while awaiting payment
  outgoing_chest_id      INT REFERENCES chests(id),   -- assigned at fill time
  manifest_posted        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes                  TEXT
);

CREATE TABLE IF NOT EXISTS order_items (
  id            SERIAL PRIMARY KEY,
  order_id      INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  kit_type_id   INT NOT NULL REFERENCES kit_types(id),
  qty_requested INT NOT NULL,
  qty_reserved  INT NOT NULL DEFAULT 0,
  qty_filled    INT NOT NULL DEFAULT 0
  -- qty_short is derived: qty_requested - qty_filled
);

CREATE TABLE IF NOT EXISTS fulfillment_log (
  id          SERIAL PRIMARY KEY,
  order_id    INT REFERENCES orders(id) ON DELETE CASCADE,
  ts          TIMESTAMPTZ NOT NULL DEFAULT now(),
  event       TEXT NOT NULL,   -- 'reserve'|'paid'|'route'|'withdraw'|'deposit'
                               -- |'unreachable'|'discrepancy'|'manifest'|'complete'|'error'
  chest_id    INT,
  kit_type_id INT,
  qty         INT,
  detail      JSONB
);

-- ---------- live stock (reservation-aware), from latest complete scan ----------
CREATE OR REPLACE VIEW kit_stock AS
SELECT kt.id AS kit_type_id, kt.name,
       count(s.*) FILTER (WHERE NOT s.withdrawn)                    AS on_hand,
       count(s.*) FILTER (WHERE NOT s.withdrawn AND NOT s.reserved) AS available
FROM kit_types kt
LEFT JOIN shulkers s
  ON s.kit_type_id = kt.id
 AND s.scan_id = (SELECT id FROM scans WHERE status='complete'
                  ORDER BY finished_at DESC LIMIT 1)
GROUP BY kt.id, kt.name;
