package com.aquarius.database;

import com.aquarius.feature.stash.KitClassifier;
import com.aquarius.feature.stash.StashModel.ChestRow;
import com.aquarius.feature.stash.StashModel.Classification;
import com.aquarius.feature.stash.StashModel.ContentItem;
import com.aquarius.feature.stash.StashModel.KitStock;
import com.aquarius.feature.stash.StashModel.KitType;
import com.aquarius.feature.stash.StashModel.ManifestLine;
import com.aquarius.feature.stash.StashModel.OrderSummary;
import com.aquarius.feature.stash.StashModel.ReservedShulker;
import org.jdbi.v3.core.Jdbi;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.aquarius.Globals.DATABASE_LOG;
import static com.aquarius.Globals.OBJECT_MAPPER;

/**
 * Relational data access for the stash system. Unlike the proxy's time-series stat databases this is a
 * plain CRUD DAO over the shared jdbi handle (no Redis insert-queue / lock model — there is one writer,
 * the bot). Self-provisions its schema from {@code resources/stash/schema.sql} on {@link #init()} so no
 * manual {@code psql} step is needed. Implements the queries from stashBot CONTEXT.md section 5.
 */
public class StashDatabase {
    private final QueryExecutor queryExecutor;
    private volatile List<KitType> catalogCache = List.of();

    public StashDatabase(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    private Jdbi jdbi() { return queryExecutor.jdbi(); }

    // ---------------------------------------------------------------- lifecycle

    public void init() {
        try {
            String sql = readResource("stash/schema.sql");
            jdbi().useHandle(h -> h.createScript(sql).execute());
            loadCatalog();
            DATABASE_LOG.info("Stash schema provisioned; {} kit types in catalog.", catalogCache.size());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed initialising stash database", e);
        }
    }

    private static String readResource(final String path) throws Exception {
        try (InputStream in = StashDatabase.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------- catalog

    public synchronized List<KitType> loadCatalog() {
        try {
            List<KitType> kits = jdbi().withHandle(h -> {
                List<KitType> out = new ArrayList<>();
                var rows = h.createQuery("SELECT id,name,display_name,color,match_strictness,signature FROM kit_types ORDER BY id")
                    .map((rs, ctx) -> new Object[]{
                        rs.getInt("id"), rs.getString("name"), rs.getString("display_name"),
                        rs.getString("color"), rs.getString("match_strictness"), rs.getString("signature")})
                    .list();
                for (Object[] r : rows) {
                    int id = (int) r[0];
                    List<ContentItem> contents = h.createQuery("SELECT item_id,count FROM kit_contents WHERE kit_type_id=:id ORDER BY slot NULLS LAST, id")
                        .bind("id", id)
                        .map((rs, ctx) -> new ContentItem(rs.getString("item_id"), rs.getInt("count")))
                        .list();
                    out.add(new KitType(id, (String) r[1], (String) r[2], (String) r[3],
                        (String) r[4], (String) r[5], contents));
                }
                return out;
            });
            this.catalogCache = kits;
            return kits;
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed loading kit catalog", e);
            return catalogCache;
        }
    }

    public List<KitType> getCatalog() {
        return catalogCache;
    }

    public @Nullable KitType findKitByName(final String name) {
        for (KitType kt : catalogCache) if (kt.name().equalsIgnoreCase(name)) return kt;
        return null;
    }

    /** Insert or update a kit definition (+ its contents + computed signature). Used by the seed command. */
    public void upsertKit(final String name, final @Nullable String display, final @Nullable String color,
                          final String strictness, final List<ContentItem> contents) {
        try {
            jdbi().useTransaction(h -> {
                String sig = KitClassifier.signatureOf(contents);
                int id = h.createUpdate("INSERT INTO kit_types(name,display_name,color,match_strictness,signature) "
                        + "VALUES(:n,:d,:c,:s,:sig) ON CONFLICT(name) DO UPDATE SET "
                        + "display_name=excluded.display_name, color=excluded.color, "
                        + "match_strictness=excluded.match_strictness, signature=excluded.signature")
                    .bind("n", name).bind("d", display).bind("c", color).bind("s", strictness).bind("sig", sig)
                    .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one();
                h.createUpdate("DELETE FROM kit_contents WHERE kit_type_id=:id").bind("id", id).execute();
                int slot = 0;
                for (ContentItem ci : contents) {
                    h.createUpdate("INSERT INTO kit_contents(kit_type_id,item_id,count,slot) VALUES(:k,:i,:c,:s)")
                        .bind("k", id).bind("i", ci.item()).bind("c", ci.count()).bind("s", slot++).execute();
                }
            });
            loadCatalog();
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed upserting kit {}", name, e);
        }
    }

    /** Seed/replace the catalog from a kits.json array (name, display_name, color, match_strictness, contents[]). */
    public int seedFromJson(final String jsonText) {
        try {
            tools.jackson.databind.JsonNode arr = OBJECT_MAPPER.readTree(jsonText);
            int n = 0;
            for (tools.jackson.databind.JsonNode k : arr) {
                if (!k.hasNonNull("name")) continue;
                String name = k.get("name").asString();
                String display = k.hasNonNull("display_name") ? k.get("display_name").asString() : null;
                String color = k.hasNonNull("color") ? k.get("color").asString() : null;
                String strict = k.hasNonNull("match_strictness") ? k.get("match_strictness").asString() : "item_and_count";
                List<ContentItem> contents = new ArrayList<>();
                tools.jackson.databind.JsonNode cs = k.get("contents");
                if (cs != null) for (tools.jackson.databind.JsonNode c : cs) {
                    contents.add(new ContentItem(c.get("item").asString(), c.get("count").asInt()));
                }
                upsertKit(name, display, color, strict, contents);
                n++;
            }
            return n;
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed seeding catalog from json", e);
            return 0;
        }
    }

    public List<KitStock> kitStock() {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT kit_type_id,name,on_hand,available FROM kit_stock ORDER BY name")
                .map((rs, ctx) -> new KitStock(rs.getInt("kit_type_id"), rs.getString("name"),
                    rs.getInt("on_hand"), rs.getInt("available")))
                .list());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed reading kit stock", e);
            return List.of();
        }
    }

    // ---------------------------------------------------------------- chests

    public int upsertChest(final String dim, final int x, final int y, final int z, final boolean isDouble, final String role) {
        try {
            return jdbi().withHandle(h -> h.createUpdate(
                    "INSERT INTO chests(dimension,x,y,z,is_double,role,reachable) VALUES(:dim,:x,:y,:z,:dbl,:role,TRUE) "
                        + "ON CONFLICT(dimension,x,y,z) DO UPDATE SET is_double=excluded.is_double, reachable=TRUE")
                .bind("dim", dim).bind("x", x).bind("y", y).bind("z", z).bind("dbl", isDouble).bind("role", role)
                .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed upserting chest {},{},{}", x, y, z, e);
            return -1;
        }
    }

    /** Set (or create) a chest with an explicit role — used to mark outgoing chests. */
    public int setChestRole(final String dim, final int x, final int y, final int z, final String role) {
        try {
            return jdbi().withHandle(h -> h.createUpdate(
                    "INSERT INTO chests(dimension,x,y,z,role,reachable) VALUES(:dim,:x,:y,:z,:role,TRUE) "
                        + "ON CONFLICT(dimension,x,y,z) DO UPDATE SET role=excluded.role")
                .bind("dim", dim).bind("x", x).bind("y", y).bind("z", z).bind("role", role)
                .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed setting chest role {},{},{}={}", x, y, z, role, e);
            return -1;
        }
    }

    /** A chest's label (or null) — used for customer-facing manifests so raw coords never leak. */
    public @Nullable String chestLabel(final int chestId) {
        try {
            return jdbi().withHandle(h -> h.createQuery("SELECT label FROM chests WHERE id=:id").bind("id", chestId)
                .mapTo(String.class).findOne().orElse(null));
        } catch (final Exception e) {
            return null;
        }
    }

    public void setChestReachable(final int chestId, final boolean reachable) {
        run("UPDATE chests SET reachable=:r WHERE id=:id", q -> q.bind("r", reachable).bind("id", chestId));
    }

    /** Register (or re-flag) a per-order outgoing chest with an optional human label. */
    public int setOutgoingChest(final String dim, final int x, final int y, final int z, final @Nullable String label) {
        try {
            return jdbi().withHandle(h -> h.createUpdate(
                    "INSERT INTO chests(dimension,x,y,z,role,label,reachable) VALUES(:dim,:x,:y,:z,'outgoing',:label,TRUE) "
                        + "ON CONFLICT(dimension,x,y,z) DO UPDATE SET role='outgoing', label=COALESCE(excluded.label, chests.label), reachable=TRUE")
                .bind("dim", dim).bind("x", x).bind("y", y).bind("z", z).bind("label", label)
                .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed registering outgoing chest {},{},{}", x, y, z, e);
            return -1;
        }
    }

    public List<ChestRow> listChests(final @Nullable String role) {
        try {
            return jdbi().withHandle(h -> {
                var q = role == null
                    ? h.createQuery("SELECT id,label,dimension,x,y,z,is_double,role,reachable FROM chests ORDER BY id")
                    : h.createQuery("SELECT id,label,dimension,x,y,z,is_double,role,reachable FROM chests WHERE role=:role ORDER BY id").bind("role", role);
                return q.map(StashDatabase::mapChest).list();
            });
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed listing chests", e);
            return List.of();
        }
    }

    private static ChestRow mapChest(java.sql.ResultSet rs, org.jdbi.v3.core.statement.StatementContext ctx) throws java.sql.SQLException {
        return new ChestRow(rs.getInt("id"), rs.getString("label"), rs.getString("dimension"),
            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getBoolean("is_double"),
            rs.getString("role"), rs.getBoolean("reachable"));
    }

    // ---------------------------------------------------------------- scans + shulkers

    public int startScan() {
        try {
            return jdbi().withHandle(h -> h.createUpdate("INSERT INTO scans(status) VALUES('running')")
                .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed starting scan", e);
            return -1;
        }
    }

    public void finishScan(final int scanId, final int scanned, final int unreached, final int found) {
        run("UPDATE scans SET status='complete', finished_at=now(), chests_scanned=:s, chests_unreached=:u, shulkers_found=:f WHERE id=:id",
            q -> q.bind("s", scanned).bind("u", unreached).bind("f", found).bind("id", scanId));
    }

    public void abortScan(final int scanId) {
        run("UPDATE scans SET status='aborted', finished_at=now() WHERE id=:id", q -> q.bind("id", scanId));
    }

    public void insertShulker(final int scanId, final int chestId, final int slot, final Classification cl,
                              final @Nullable String customName, final @Nullable String color,
                              final List<ContentItem> contents) {
        String json;
        try { json = OBJECT_MAPPER.writeValueAsString(contents); } catch (final Exception e) { json = null; }
        final String contentsJson = json;
        run("INSERT INTO shulkers(scan_id,chest_id,slot,kit_type_id,classification,confidence,custom_name,color,content_hash,contents) "
                + "VALUES(:scan,:chest,:slot,:kit,:cls,:conf,:name,:color,:hash,CAST(:contents AS jsonb))",
            q -> q.bind("scan", scanId).bind("chest", chestId).bind("slot", slot)
                .bind("kit", cl.kitTypeId()).bind("cls", cl.classification()).bind("conf", cl.confidence())
                .bind("name", customName).bind("color", color).bind("hash", cl.contentHash()).bind("contents", contentsJson));
    }

    /** Distinct content signatures in the latest complete scan that aren't matched to a kit (for {@code .ss dump}). */
    public List<String> unknownSignatures() {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT content_hash, count(*) n, max(contents::text) sample FROM shulkers "
                        + "WHERE classification<>'matched' AND scan_id=(SELECT id FROM scans WHERE status='complete' ORDER BY finished_at DESC LIMIT 1) "
                        + "GROUP BY content_hash ORDER BY n DESC")
                .map((rs, ctx) -> rs.getInt("n") + "x  " + rs.getString("sample") + "  [" + rs.getString("content_hash") + "]")
                .list());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed reading unknown signatures", e);
            return List.of();
        }
    }

    // ---------------------------------------------------------------- orders: intake / reserve

    public int createOrder(final String discordUserId, final @Nullable String channelId) {
        try {
            return jdbi().withHandle(h -> h.createUpdate(
                    "INSERT INTO orders(discord_user_id,discord_channel_id,status) VALUES(:u,:c,'awaiting_payment')")
                .bind("u", discordUserId).bind("c", channelId)
                .executeAndReturnGeneratedKeys("id").mapTo(Integer.class).one());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed creating order", e);
            return -1;
        }
    }

    public void addOrderItem(final int orderId, final int kitTypeId, final int qtyRequested) {
        run("INSERT INTO order_items(order_id,kit_type_id,qty_requested) VALUES(:o,:k,:q)",
            q -> q.bind("o", orderId).bind("k", kitTypeId).bind("q", qtyRequested));
    }

    /** Atomic soft-hold (doc section 5): reserve up to {@code qty} free shulkers of a kit; returns the number held. */
    public int softHold(final int kitTypeId, final int qty, final int orderId) {
        try {
            return jdbi().inTransaction(h -> {
                int held = h.createUpdate(
                        "WITH pick AS (SELECT id FROM shulkers WHERE kit_type_id=:kit AND NOT withdrawn AND NOT reserved "
                            + "AND scan_id=(SELECT id FROM scans WHERE status='complete' ORDER BY finished_at DESC LIMIT 1) "
                            + "ORDER BY chest_id, slot LIMIT :qty FOR UPDATE SKIP LOCKED) "
                            + "UPDATE shulkers s SET reserved=TRUE, reserved_by_order=:order FROM pick WHERE s.id=pick.id")
                    .bind("kit", kitTypeId).bind("qty", qty).bind("order", orderId).execute();
                h.createUpdate("UPDATE order_items SET qty_reserved=:held WHERE order_id=:o AND kit_type_id=:k")
                    .bind("held", held).bind("o", orderId).bind("k", kitTypeId).execute();
                return held;
            });
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed soft-holding kit {} for order {}", kitTypeId, orderId, e);
            return 0;
        }
    }

    public void setReservationExpiry(final int orderId, final int minutes) {
        run("UPDATE orders SET reservation_expires_at=now() + make_interval(mins => :m), updated_at=now() WHERE id=:id",
            q -> q.bind("m", minutes).bind("id", orderId));
    }

    /** Reaper: release shulkers held by expired unpaid orders and mark those orders expired. Returns # expired. */
    public int releaseExpiredHolds() {
        try {
            return jdbi().inTransaction(h -> {
                List<Integer> dead = h.createQuery(
                        "SELECT id FROM orders WHERE status='awaiting_payment' AND reservation_expires_at < now()")
                    .mapTo(Integer.class).list();
                if (dead.isEmpty()) return 0;
                h.createUpdate("UPDATE shulkers SET reserved=FALSE, reserved_by_order=NULL WHERE reserved_by_order IN (<ids>)")
                    .bindList("ids", dead).execute();
                h.createUpdate("UPDATE orders SET status='expired', updated_at=now() WHERE id IN (<ids>)")
                    .bindList("ids", dead).execute();
                return dead.size();
            });
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed releasing expired holds", e);
            return 0;
        }
    }

    public void markPaid(final int orderId, final @Nullable String paymentRef) {
        run("UPDATE orders SET status='paid', paid_at=now(), payment_ref=:ref, reservation_expires_at=NULL, updated_at=now() WHERE id=:id",
            q -> q.bind("ref", paymentRef).bind("id", orderId));
    }

    public void setOrderStatus(final int orderId, final String status) {
        run("UPDATE orders SET status=:s, updated_at=now() WHERE id=:id", q -> q.bind("s", status).bind("id", orderId));
    }

    public void markManifestPosted(final int orderId) {
        run("UPDATE orders SET manifest_posted=TRUE WHERE id=:id", q -> q.bind("id", orderId));
    }

    /** Cancel an order if it hasn't started filling; releases its holds. Returns true if cancelled. */
    public boolean cancelOrder(final int orderId) {
        try {
            return jdbi().inTransaction(h -> {
                String status = h.createQuery("SELECT status FROM orders WHERE id=:id").bind("id", orderId)
                    .mapTo(String.class).findOne().orElse(null);
                if (status == null) return false;
                if (status.equals("filling") || status.equals("ready") || status.equals("delivered")) return false;
                h.createUpdate("UPDATE shulkers SET reserved=FALSE, reserved_by_order=NULL WHERE reserved_by_order=:id")
                    .bind("id", orderId).execute();
                h.createUpdate("UPDATE orders SET status='cancelled', updated_at=now() WHERE id=:id").bind("id", orderId).execute();
                return true;
            });
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed cancelling order {}", orderId, e);
            return false;
        }
    }

    public @Nullable OrderSummary orderSummary(final int orderId) {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT id,discord_user_id,discord_channel_id,status,outgoing_chest_id,payment_ref FROM orders WHERE id=:id")
                .bind("id", orderId)
                .map((rs, ctx) -> new OrderSummary(rs.getInt("id"), rs.getString("discord_user_id"),
                    rs.getString("discord_channel_id"), rs.getString("status"),
                    (Integer) rs.getObject("outgoing_chest_id"), rs.getString("payment_ref")))
                .findOne().orElse(null));
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed reading order {}", orderId, e);
            return null;
        }
    }

    // ---------------------------------------------------------------- fulfillment

    /** Pick a free outgoing chest (doc section 5) and assign it to the order. Null if none free. */
    public @Nullable ChestRow assignOutgoingChest(final int orderId) {
        try {
            return jdbi().inTransaction(h -> {
                Optional<ChestRow> chest = h.createQuery(
                        "SELECT id,label,dimension,x,y,z,is_double,role,reachable FROM chests c WHERE c.role='outgoing' AND c.reachable "
                            + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.outgoing_chest_id=c.id AND o.status IN ('paid','filling','ready')) "
                            + "ORDER BY c.id LIMIT 1")
                    .map(StashDatabase::mapChest).findOne();
                if (chest.isEmpty()) return null;
                h.createUpdate("UPDATE orders SET outgoing_chest_id=:c, updated_at=now() WHERE id=:o")
                    .bind("c", chest.get().id()).bind("o", orderId).execute();
                return chest.get();
            });
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed assigning outgoing chest for order {}", orderId, e);
            return null;
        }
    }

    public List<ReservedShulker> loadReservedShulkers(final int orderId) {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT s.id,s.chest_id,s.slot,s.kit_type_id,kt.name AS kit_name,s.custom_name,s.color,c.x,c.y,c.z,c.is_double "
                        + "FROM shulkers s JOIN chests c ON c.id=s.chest_id LEFT JOIN kit_types kt ON kt.id=s.kit_type_id "
                        + "WHERE s.reserved_by_order=:o AND NOT s.withdrawn ORDER BY s.chest_id, s.slot")
                .bind("o", orderId)
                .map((rs, ctx) -> new ReservedShulker(rs.getInt("id"), rs.getInt("chest_id"), rs.getInt("slot"),
                    (Integer) rs.getObject("kit_type_id"), rs.getString("kit_name"),
                    rs.getString("custom_name"), rs.getString("color"),
                    rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getBoolean("is_double")))
                .list());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed loading reserved shulkers for order {}", orderId, e);
            return List.of();
        }
    }

    public void markWithdrawn(final int shulkerId) {
        run("UPDATE shulkers SET withdrawn=TRUE WHERE id=:id", q -> q.bind("id", shulkerId));
    }

    /** Release any still-reserved (not yet withdrawn) shulkers held by an order — after a fill or on failure. */
    public void releaseRemaining(final int orderId) {
        run("UPDATE shulkers SET reserved=FALSE, reserved_by_order=NULL WHERE reserved_by_order=:id AND NOT withdrawn",
            q -> q.bind("id", orderId));
    }

    public void addFilled(final int orderId, final int kitTypeId, final int n) {
        run("UPDATE order_items SET qty_filled=qty_filled + :n WHERE order_id=:o AND kit_type_id=:k",
            q -> q.bind("n", n).bind("o", orderId).bind("k", kitTypeId));
    }

    public void logFulfillment(final int orderId, final String event, final @Nullable Integer chestId,
                               final @Nullable Integer kitTypeId, final @Nullable Integer qty, final @Nullable Object detail) {
        String json;
        try { json = detail == null ? null : OBJECT_MAPPER.writeValueAsString(detail); } catch (final Exception e) { json = null; }
        final String detailJson = json;
        run("INSERT INTO fulfillment_log(order_id,event,chest_id,kit_type_id,qty,detail) VALUES(:o,:e,:c,:k,:q,CAST(:d AS jsonb))",
            q -> q.bind("o", orderId).bind("e", event).bind("c", chestId).bind("k", kitTypeId).bind("q", qty).bind("d", detailJson));
    }

    public List<ManifestLine> manifestRows(final int orderId) {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT kt.name AS name, oi.qty_requested, oi.qty_filled FROM order_items oi "
                        + "JOIN kit_types kt ON kt.id=oi.kit_type_id WHERE oi.order_id=:o ORDER BY kt.name")
                .bind("o", orderId)
                .map((rs, ctx) -> new ManifestLine(rs.getString("name"), rs.getInt("qty_requested"), rs.getInt("qty_filled")))
                .list());
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed building manifest for order {}", orderId, e);
            return List.of();
        }
    }

    public List<String> recentLog(final int orderId, final int limit) {
        try {
            return jdbi().withHandle(h -> h.createQuery(
                    "SELECT to_char(ts,'HH24:MI:SS') t, event, qty FROM fulfillment_log WHERE order_id=:o ORDER BY ts DESC LIMIT :n")
                .bind("o", orderId).bind("n", limit)
                .map((rs, ctx) -> rs.getString("t") + "  " + rs.getString("event")
                    + (rs.getObject("qty") != null ? " x" + rs.getInt("qty") : ""))
                .list());
        } catch (final Exception e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------- helper

    private interface Binder { org.jdbi.v3.core.statement.Update apply(org.jdbi.v3.core.statement.Update u); }

    private void run(final String sql, final Binder binder) {
        try {
            jdbi().useHandle(h -> binder.apply(h.createUpdate(sql)).execute());
        } catch (final Exception e) {
            DATABASE_LOG.error("Stash query failed: {}", sql, e);
        }
    }
}
