package com.aquarius.database;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import tools.jackson.databind.JsonNode;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.aquarius.Globals.DATABASE_LOG;
import static com.aquarius.Globals.OBJECT_MAPPER;

/**
 * Redis IPC for the order pipeline (stashBot CONTEXT.md section 7.3), built on the proxy's existing
 * {@link RedisClient} (Redisson). Three channels:
 * <ul>
 *   <li>{@code orders:queue} — a reliable list the OrderService pushes paid jobs onto and the OrderFiller
 *       polls (one job at a time);</li>
 *   <li>{@code orders:status} — live fill progress the filler publishes and the OrderService relays to Discord;</li>
 *   <li>{@code orders:paid} — an inbound signal the external shop / TicketTool bot publishes to trigger a fill.</li>
 * </ul>
 */
public class StashQueue {
    public static final String QUEUE = "orders:queue";
    public static final String STATUS = "orders:status";
    public static final String PAID = "orders:paid";

    private final RedisClient redisClient;
    private Integer paidListenerId;
    private Integer statusListenerId;

    public StashQueue(final RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    private RBlockingQueue<String> queue() {
        return redisClient.getRedissonClient().getBlockingQueue(QUEUE);
    }
    private RTopic topic(final String name) {
        return redisClient.getRedissonClient().getTopic(name);
    }

    /** Push a paid job. Payload: {@code {"order_id":N}}. */
    public void enqueueOrder(final int orderId) {
        try {
            queue().offer("{\"order_id\":" + orderId + "}");
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed enqueueing order {}", orderId, e);
        }
    }

    /** Non-blocking pop of the next job's order id, or null if the queue is empty / unavailable. */
    public Integer pollOrder() {
        try {
            String msg = queue().poll();
            if (msg == null) return null;
            JsonNode n = OBJECT_MAPPER.readTree(msg);
            return n.has("order_id") ? n.get("order_id").asInt() : null;
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed polling order queue", e);
            return null;
        }
    }

    /** Publish a status update JSON to {@code orders:status}. */
    public void publishStatus(final String json) {
        try {
            topic(STATUS).publish(json);
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed publishing order status", e);
        }
    }

    /** Subscribe to the inbound paid signal. Listener gets (order_id, payment_ref?). */
    public void subscribePaid(final BiConsumer<Integer, String> handler) {
        try {
            MessageListener<String> l = (channel, msg) -> {
                try {
                    JsonNode n = OBJECT_MAPPER.readTree(msg);
                    if (!n.has("order_id")) return;
                    handler.accept(n.get("order_id").asInt(), n.has("payment_ref") ? n.get("payment_ref").asString() : null);
                } catch (final Exception e) {
                    DATABASE_LOG.error("Bad orders:paid payload: {}", msg, e);
                }
            };
            paidListenerId = topic(PAID).addListener(String.class, l);
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed subscribing to orders:paid", e);
        }
    }

    /** Subscribe to status updates (the OrderService relays these to Discord). */
    public void subscribeStatus(final Consumer<String> handler) {
        try {
            MessageListener<String> l = (channel, msg) -> handler.accept(msg);
            statusListenerId = topic(STATUS).addListener(String.class, l);
        } catch (final Exception e) {
            DATABASE_LOG.error("Failed subscribing to orders:status", e);
        }
    }

    public void shutdown() {
        try {
            if (paidListenerId != null) topic(PAID).removeListener(paidListenerId);
            if (statusListenerId != null) topic(STATUS).removeListener(statusListenerId);
        } catch (final Exception e) {
            DATABASE_LOG.warn("Error removing stash queue listeners", e);
        }
        paidListenerId = null;
        statusListenerId = null;
    }
}
