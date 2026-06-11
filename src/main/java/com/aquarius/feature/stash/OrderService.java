package com.aquarius.feature.stash;

import com.aquarius.database.StashDatabase;
import com.aquarius.database.StashQueue;
import com.aquarius.discord.Embed;
import com.aquarius.feature.stash.StashModel.KitType;
import com.aquarius.feature.stash.StashModel.ManifestLine;
import com.aquarius.feature.stash.StashModel.OrderSummary;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DISCORD;
import static com.aquarius.Globals.EXECUTOR;
import static com.aquarius.Globals.OBJECT_MAPPER;
import static com.aquarius.Globals.STASH_LOG;

/**
 * The non-in-world glue of the order pipeline (stashBot CONTEXT.md section 7.4) — runs natively in the proxy
 * instead of a separate discord.js bot. Owns: order intake + soft-hold ({@link #placeOrder}), the reaper that
 * releases expired unpaid holds, the inbound {@code orders:paid} adapter (the external shop / TicketTool bot
 * publishes; we mark paid + enqueue), and posting the final manifest embed to Discord. Order/stock/catalog
 * commands ({@link com.aquarius.command.impl.OrderCommand}) call into this.
 *
 * <p>Lifecycle is driven by {@code DatabaseManager.startStashDatabase()} so it shares the live DB + queue.
 */
public final class OrderService {
    private static final OrderService INSTANCE = new OrderService();
    public static OrderService get() { return INSTANCE; }
    private OrderService() {}

    private @Nullable StashDatabase db;
    private @Nullable StashQueue queue;
    private @Nullable ScheduledFuture<?> reaper;

    public synchronized void start(final StashDatabase db, final StashQueue queue) {
        this.db = db;
        this.queue = queue;
        queue.subscribePaid(this::onPaid);
        queue.subscribeStatus(this::onStatus);
        if (reaper != null) reaper.cancel(false);
        reaper = EXECUTOR.scheduleWithFixedDelay(this::runReaper, 60, 60, TimeUnit.SECONDS);
        STASH_LOG.info("OrderService started (paid-adapter + status relay + reaper).");
    }

    public synchronized void stop() {
        if (reaper != null) reaper.cancel(false);
        reaper = null;
        this.db = null;
        this.queue = null;
    }

    public boolean isReady() { return db != null && queue != null; }

    // ---------------------------------------------------------------- intake

    /** Result of {@link #placeOrder}: ok + order id + a human summary + whether any kit was short. */
    public record PlaceResult(boolean ok, int orderId, String message, boolean partial) {}

    /**
     * Create an order, soft-hold stock per kit, and set the unpaid TTL. Does NOT enqueue — the order waits
     * for an {@code orders:paid} signal. {@code kits} maps kit name -> quantity (insertion order preserved).
     */
    public synchronized PlaceResult placeOrder(final String discordUserId, final @Nullable String channelId,
                                               final Map<String, Integer> kits) {
        if (db == null) return new PlaceResult(false, -1, "Stash database is not enabled.", false);
        // validate kit names first
        for (String name : kits.keySet()) {
            if (db.findKitByName(name) == null) {
                return new PlaceResult(false, -1, "Unknown kit: " + name + ". Try .catalog for valid kits.", false);
            }
        }
        int orderId = db.createOrder(discordUserId, channelId);
        if (orderId == -1) return new PlaceResult(false, -1, "Failed creating the order.", false);
        StringBuilder sb = new StringBuilder();
        boolean partial = false;
        int total = 0;
        for (Map.Entry<String, Integer> e : kits.entrySet()) {
            KitType kt = db.findKitByName(e.getKey());
            int qty = Math.max(1, e.getValue());
            db.addOrderItem(orderId, kt.id(), qty);
            int held = db.softHold(kt.id(), qty, orderId);
            total += held;
            if (held < qty) partial = true;
            sb.append("\n• ").append(kt.name()).append(": ").append(held).append('/').append(qty)
                .append(held < qty ? " (short " + (qty - held) + ")" : "");
        }
        db.setReservationExpiry(orderId, CONFIG.client.extra.orderFiller.softHoldMinutes);
        db.logFulfillment(orderId, "reserve", null, null, total, null);
        return new PlaceResult(true, orderId, sb.toString(), partial);
    }

    /** Mark paid + enqueue (used by the command's manual paid trigger). */
    public synchronized boolean markPaidAndEnqueue(final int orderId, final @Nullable String paymentRef) {
        if (db == null || queue == null) return false;
        OrderSummary s = db.orderSummary(orderId);
        if (s == null) return false;
        onPaid(orderId, paymentRef);
        return true;
    }

    // ---------------------------------------------------------------- redis adapters

    /** Inbound paid signal: mark the order paid, clear its TTL, and push the fill job. */
    private void onPaid(final int orderId, final @Nullable String paymentRef) {
        StashDatabase d = db;
        StashQueue q = queue;
        if (d == null || q == null) return;
        OrderSummary s = d.orderSummary(orderId);
        if (s == null) { STASH_LOG.warn("orders:paid for unknown order {}", orderId); return; }
        if (!s.status().equals("awaiting_payment") && !s.status().equals("paid")) {
            STASH_LOG.warn("orders:paid for order {} in status {} — ignoring", orderId, s.status());
            return;
        }
        d.markPaid(orderId, paymentRef);
        d.logFulfillment(orderId, "paid", null, null, null, paymentRef);
        q.enqueueOrder(orderId);
        STASH_LOG.info("Order {} paid (ref {}) -> enqueued for fill.", orderId, paymentRef);
    }

    /** OrderFiller status updates: relay terminal states, and post the manifest on the {@code manifest} signal. */
    private void onStatus(final String json) {
        if (db == null) return;
        try {
            JsonNode n = OBJECT_MAPPER.readTree(json);
            if (!n.has("order_id") || !n.has("status")) return;
            int orderId = n.get("order_id").asInt();
            String status = n.get("status").asString();
            if (status.equals("manifest")) {
                postManifest(orderId);
            } else if (status.equals("delivered") || status.equals("ready") || status.equals("failed")) {
                STASH_LOG.info("Order {} -> {}", orderId, status);
            }
        } catch (final Exception e) {
            STASH_LOG.error("Bad orders:status payload: {}", json, e);
        }
    }

    private void runReaper() {
        StashDatabase d = db;
        if (d == null) return;
        int n = d.releaseExpiredHolds();
        if (n > 0) STASH_LOG.info("Reaper released holds for {} expired order(s).", n);
    }

    // ---------------------------------------------------------------- manifest

    /** Build + post the order manifest embed (coords never included — only the outgoing chest label). */
    public void postManifest(final int orderId) {
        StashDatabase d = db;
        if (d == null) return;
        OrderSummary s = d.orderSummary(orderId);
        if (s == null) return;
        List<ManifestLine> rows = d.manifestRows(orderId);
        int shortTotal = rows.stream().mapToInt(ManifestLine::shortQty).sum();

        Embed e = Embed.builder()
            .title("Order #" + orderId + " — " + (shortTotal == 0 ? "delivered" : "partial"))
            .color(shortTotal == 0 ? CONFIG.theme.success.color() : CONFIG.theme.inQueue.color());
        if (s.discordUserId() != null) e.addField("Buyer", "<@" + s.discordUserId() + ">", true);
        String outLabel = s.outgoingChestId() == null ? "—"
            : (d.chestLabel(s.outgoingChestId()) != null ? d.chestLabel(s.outgoingChestId()) : "chest #" + s.outgoingChestId());
        e.addField("Pickup", outLabel, true);
        StringBuilder body = new StringBuilder();
        for (ManifestLine r : rows) {
            body.append("• ").append(r.kitName()).append(": ").append(r.filled()).append('/').append(r.requested());
            if (r.shortQty() > 0) body.append("  ⚠ short ").append(r.shortQty());
            body.append('\n');
        }
        e.description(body.length() == 0 ? "(no items)" : body.toString());
        if (shortTotal > 0) e.footer("Some kits were short of stock — see the lines marked ⚠", null);

        DISCORD.sendEmbedMessageToChannelId(CONFIG.client.extra.orderFiller.manifestChannelId, e);
    }
}
