package com.aquarius.feature.viewer;

import com.aquarius.cache.data.chunk.Chunk;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandSources;
import com.aquarius.feature.highways.GriefMap;
import com.aquarius.feature.map.Brightness;
import com.aquarius.feature.map.MapGenerator;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.module.impl.ElytraPilot;
import com.aquarius.Proxy;
import com.aquarius.feature.player.raycast.RaycastHelper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.ScheduledFuture;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static com.aquarius.Globals.BLOCK_DATA;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.COMMAND;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.EXECUTOR;
import static com.aquarius.Globals.MAP_BLOCK_COLOR;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.Globals.SERVER_LOG;
import static com.aquarius.Globals.saveConfigAsync;

/**
 * Read-only viewer feed for the Aquarius Bot Manager's live map / POV viewer. A new instance per connection.
 *
 * <p>Endpoints (all {@code GET}):
 * <ul>
 *   <li>{@code /viewer/state.json} — the bot's live player state (position, look, health, food).</li>
 * </ul>
 * The 2D map ({@code /viewer/map.png}) and the POV chunk feed ({@code /viewer/chunks}) build on this. No auth — the
 * server is loopback-bound by config and the manager relays it over its own authenticated tunnel; responses are
 * CORS-open so the dashboard UI can fetch them directly.
 */
public final class ViewerHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Gson GSON = new Gson();

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        final String path = req.uri().split("\\?", 2)[0];
        // Control: command execution + live config writes are the POST endpoints (opt-in via server.viewer.control).
        if (path.equals("/control/command")) {
            handleControlCommand(ctx, req);
            return;
        }
        if (path.equals("/control/config") && req.method() == HttpMethod.POST) {
            handleControlConfigSet(ctx, req);
            return;
        }
        if (req.method() != HttpMethod.GET) {
            respondJson(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{}");
            return;
        }
        if (path.equals("/control/config")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(controlConfig()));
            return;
        }
        if (path.equals("/control/state")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(controlState()));
            return;
        }
        if (path.equals("/control/lookingat")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(lookingAt()));
            return;
        }
        if (path.equals("/control/commands")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(commandList()));
            return;
        }
        if (path.equals("/viewer/inventory")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(inventoryState()));
            return;
        }
        if (path.equals("/viewer/state.json")) {
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(state()));
            return;
        }
        if (path.equals("/viewer/map.png")) {
            try {
                final int size = clampSize(intParam(req.uri(), "size", 256));
                // World block coord at the image centre (the map renders centred on the player's chunk).
                final int cx = CACHE.getChunkCache().getCenterX() * 16;
                final int cz = CACHE.getChunkCache().getCenterZ() * 16;
                respondPng(ctx, renderMapPng(size), cx, cz, size);
            } catch (final Exception e) {
                SERVER_LOG.warn("viewer map render failed", e);
                respondJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"map render failed\"}");
            }
            return;
        }
        if (path.equals("/viewer/chunks")) {
            try {
                final int r = Math.max(8, Math.min(64, intParam(req.uri(), "r", 48)));
                final int yb = Math.max(8, Math.min(96, intParam(req.uri(), "yb", 48)));
                final int ya = Math.max(8, Math.min(96, intParam(req.uri(), "ya", 48)));
                respondBytes(ctx, renderChunks(r, yb, ya));
            } catch (final Exception e) {
                SERVER_LOG.warn("viewer chunks failed", e);
                respondJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"chunks failed\"}");
            }
            return;
        }
        if (path.equals("/viewer/stream")) {
            startStream(ctx);
            return;
        }
        respondJson(ctx, HttpResponseStatus.NOT_FOUND, "{}");
    }

    /** ~20 Hz state push interval (ms). */
    private static final long STREAM_PERIOD_MS = 50;
    private ScheduledFuture<?> streamTask;

    /**
     * Server-Sent Events: open a chunked {@code text/event-stream} response and push the live {@link #state()} as a
     * {@code data:} frame every {@link #STREAM_PERIOD_MS}ms until the client disconnects. The manager relays this
     * straight through; the dashboard's EventSource consumes it (and falls back to polling if it isn't available).
     */
    private void startStream(final ChannelHandlerContext ctx) {
        final HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=utf-8");
        res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        HttpUtil.setTransferEncodingChunked(res, true);
        ctx.writeAndFlush(res);
        streamTask = ctx.executor().scheduleAtFixedRate(() -> {
            final var ch = ctx.channel();
            if (!ch.isActive()) {
                return;
            }
            if (!ch.isWritable()) {
                return;                // drop this frame under backpressure rather than queueing unboundedly
            }
            final String frame;
            try {
                frame = "data: " + GSON.toJson(state()) + "\n\n";
            } catch (final Exception e) {
                return;                // a transient cache read race — skip this frame
            }
            ctx.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(frame, StandardCharsets.UTF_8)));
        }, 0, STREAM_PERIOD_MS, TimeUnit.MILLISECONDS);
        ctx.channel().closeFuture().addListener(f -> cancelStream());
    }

    private void cancelStream() {
        if (streamTask != null) {
            streamTask.cancel(false);
            streamTask = null;
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        cancelStream();
        super.channelInactive(ctx);
    }

    /**
     * Serializes a voxel box around the bot for the POV renderer: header (origin XYZ, size XYZ) + a 64-entry base
     * map-color palette (RGB) + one byte per block (base mapColorId, 0 = air/skip), all deflate-compressed (the box
     * is mostly air/uniform so it shrinks hugely). Block index = (y*sz + z)*sx + x.
     */
    private byte[] renderChunks(final int r, final int yBelow, final int yAbove) throws Exception {
        final var pc = CACHE.getPlayerCache();
        final var cc = CACHE.getChunkCache();
        final int ox = (int) Math.floor(pc.getX()) - r;
        final int oz = (int) Math.floor(pc.getZ()) - r;
        final int oy = (int) Math.floor(pc.getY()) - yBelow;
        final int sx = 2 * r, sz = 2 * r, sy = yBelow + yAbove;
        final byte[] vox = new byte[sx * sy * sz];
        for (int z = 0; z < sz; z++) {
            final int wz = oz + z;
            for (int x = 0; x < sx; x++) {
                final int wx = ox + x;
                final Chunk chunk = cc.get(wx >> 4, wz >> 4);
                if (chunk == null) {
                    continue;
                }
                final int minY = chunk.minY(), maxY = chunk.maxY();
                for (int y = 0; y < sy; y++) {
                    final int wy = oy + y;
                    if (wy < minY || wy >= maxY) {
                        continue;
                    }
                    final var bd = BLOCK_DATA.getBlockDataFromBlockStateId(chunk.getBlockStateId(wx & 15, wy, wz & 15));
                    final int mc = bd == null ? 0 : bd.mapColorId();
                    if (mc > 0) {
                        vox[(y * sz + z) * sx + x] = (byte) mc;
                    }
                }
            }
        }
        final ByteArrayOutputStream raw = new ByteArrayOutputStream();
        final DataOutputStream d = new DataOutputStream(raw);
        d.writeInt(ox);
        d.writeInt(oy);
        d.writeInt(oz);
        d.writeShort(sx);
        d.writeShort(sy);
        d.writeShort(sz);
        for (int i = 0; i < 64; i++) {
            final int rgb = MAP_BLOCK_COLOR.getColor(i);
            d.write((rgb >> 16) & 0xFF);
            d.write((rgb >> 8) & 0xFF);
            d.write(rgb & 0xFF);
        }
        d.write(vox);
        d.flush();
        final ByteArrayOutputStream comp = new ByteArrayOutputStream();
        final Deflater def = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(comp, def)) {
            final byte[] rb = raw.toByteArray();
            dos.write(rb, 0, rb.length);
        }
        def.end();
        return comp.toByteArray();
    }

    private static final int MAX_ENTITIES = 64;
    private static final int MAX_GRIEF = 200;

    private Map<String, Object> state() {
        final var pc = CACHE.getPlayerCache();
        final double px = pc.getX(), pz = pc.getZ();
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", px);
        m.put("y", pc.getY());
        m.put("z", pz);
        m.put("yaw", pc.getYaw());
        m.put("pitch", pc.getPitch());
        m.put("health", pc.getThePlayer().getHealth());
        m.put("food", pc.getThePlayer().getFood());
        final var dim = CACHE.getChunkCache().getCurrentDimension();
        m.put("dimension", dim != null ? dim.name() : "?");

        final ElytraPilot pilot = MODULE.get(ElytraPilot.class);
        if (pilot != null) {
            m.put("flightPhase", pilot.viewerPhase());
            m.put("band", pilot.viewerBand());
            List<int[]> reroute = pilot.viewerReroute();
            if (!reroute.isEmpty()) {
                m.put("reroute", reroute);
            }
            int[] target = pilot.viewerTarget();
            if (target != null) {
                m.put("target", target);
            }
            final List<int[]> grief = new ArrayList<>();
            for (GriefMap.Hazard h : pilot.viewerGrief()) {
                grief.add(new int[] {h.x(), h.z()});
                if (grief.size() >= MAX_GRIEF) {
                    break;
                }
            }
            if (!grief.isEmpty()) {
                m.put("grief", grief);
            }
        } else {
            m.put("flightPhase", "IDLE");
        }

        // Nearest entities (excluding self), capped.
        final int selfId = pc.getEntityId();
        final List<Entity> ents = CACHE.getEntityCache().snapshot();
        ents.sort(Comparator.comparingDouble(e -> Math.hypot(e.getX() - px, e.getZ() - pz)));
        final List<Map<String, Object>> out = new ArrayList<>();
        for (Entity e : ents) {
            if (e.getEntityId() == selfId) {
                continue;
            }
            final Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getEntityId());
            em.put("type", e.getEntityType().name());
            em.put("x", e.getX());
            em.put("y", e.getY());
            em.put("z", e.getZ());
            out.add(em);
            if (out.size() >= MAX_ENTITIES) {
                break;
            }
        }
        m.put("entities", out);
        m.put("t", System.currentTimeMillis());
        return m;
    }

    // ---------------------------------------------------------------- inventory / armor / vitals (read-only)

    /** GET /viewer/inventory — vitals + armor pieces + hands + the main/hotbar grid, for live monitoring. */
    private Map<String, Object> inventoryState() {
        final var pc = CACHE.getPlayerCache();
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("health", pc.getThePlayer().getHealth());
        m.put("food", pc.getThePlayer().getFood());

        final Map<String, Object> armor = new LinkedHashMap<>();
        armor.put("helmet", itemJson(pc.getEquipment(EquipmentSlot.HELMET)));
        armor.put("chestplate", itemJson(pc.getEquipment(EquipmentSlot.CHESTPLATE)));
        armor.put("leggings", itemJson(pc.getEquipment(EquipmentSlot.LEGGINGS)));
        armor.put("boots", itemJson(pc.getEquipment(EquipmentSlot.BOOTS)));
        m.put("armor", armor);
        m.put("mainHand", itemJson(pc.getEquipment(Hand.MAIN_HAND)));
        m.put("offHand", itemJson(pc.getEquipment(Hand.OFF_HAND)));

        final var inv = pc.getPlayerInventory();
        final List<Map<String, Object>> hotbar = new ArrayList<>();
        for (int i = 36; i <= 44 && i < inv.size(); i++) hotbar.add(itemJson(inv.get(i)));
        final List<Map<String, Object>> main = new ArrayList<>();
        for (int i = 9; i <= 35 && i < inv.size(); i++) main.add(itemJson(inv.get(i)));
        m.put("hotbar", hotbar);
        m.put("main", main);

        int totems = 0;
        for (int i = 9; i < inv.size(); i++) {
            final ItemStack s = inv.get(i);
            if (s == null) continue;
            final var d = ItemRegistry.REGISTRY.get(s.getId());
            if (d != null && "totem_of_undying".equals(d.name())) totems += s.getAmount();
        }
        m.put("totems", totems);
        m.put("t", System.currentTimeMillis());
        return m;
    }

    /** Compact JSON for one item stack (null for an empty slot): name, count, durability, enchanted flag. */
    private Map<String, Object> itemJson(final ItemStack s) {
        if (s == null) return null;
        final var data = ItemRegistry.REGISTRY.get(s.getId());
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", data != null ? data.name() : ("id:" + s.getId()));
        m.put("count", s.getAmount());
        if (data != null) {
            final Integer maxDamage = data.components().get(DataComponentTypes.MAX_DAMAGE);
            if (maxDamage != null) {
                final Integer damage = s.getDataComponentsOrEmpty().get(DataComponentTypes.DAMAGE);
                m.put("dur", maxDamage - (damage == null ? 0 : damage));
                m.put("max", maxDamage);
            }
        }
        final var ench = s.getDataComponentsOrEmpty().get(DataComponentTypes.ENCHANTMENTS);
        if (ench != null) m.put("ench", true);
        return m;
    }

    // ---------------------------------------------------------------- control (opt-in command execution)

    private static final class ControlCommandRequest { String command; }

    /** POST /control/command — run a command as the loopback/terminal source and return its structured output. */
    private void handleControlCommand(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        if (req.method() != HttpMethod.POST) {
            respondJson(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{}");
            return;
        }
        if (!CONFIG.server.viewer.control) {
            respondJson(ctx, HttpResponseStatus.FORBIDDEN, "{\"error\":\"control disabled\"}");
            return;
        }
        final String body = req.content().toString(StandardCharsets.UTF_8);
        String cmd;
        try {
            final ControlCommandRequest r = GSON.fromJson(body, ControlCommandRequest.class);
            cmd = r == null ? null : r.command;
        } catch (final Exception e) {
            respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"bad request\"}");
            return;
        }
        if (cmd == null || cmd.isBlank()) {
            respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"empty command\"}");
            return;
        }
        final String command = cmd.strip();
        // run off the netty thread (a command may touch the game loop); respond when it's done
        EXECUTOR.execute(() -> respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(runControlCommand(command))));
    }

    private Map<String, Object> runControlCommand(final String command) {
        SERVER_LOG.info("Control command: {}", command);
        final Map<String, Object> out = new LinkedHashMap<>();
        final List<String> lines = new ArrayList<>();
        try {
            final CommandContext c = CommandContext.create(command, CommandSources.TERMINAL);
            COMMAND.execute(c);
            final var embed = c.getEmbed();
            if (embed.title() != null) lines.add(embed.title());
            if (embed.description() != null) lines.add(embed.description());
            for (final var f : embed.fields()) lines.add(f.name() + ": " + f.value());
            lines.addAll(c.getMultiLineOutput());
            out.put("title", embed.title());
        } catch (final Exception e) {
            lines.add("error: " + e.getMessage());
            out.put("error", true);
        }
        if (lines.isEmpty()) lines.add("ok");
        out.put("lines", lines);
        return out;
    }

    // ---- live config read/write (opt-in via server.viewer.control) ----

    /** Config field paths that must never be exposed or written through this API (auth/proxy/discord/db secrets). */
    private static final Set<String> SECRET_PATHS = Set.of(
            "authentication.password",
            "client.connectionProxy.user",
            "client.connectionProxy.password",
            "discord.token",
            "database.password");

    private static boolean isSecretPath(final String p) {
        if (SECRET_PATHS.contains(p)) return true;
        final String lp = p.toLowerCase(Locale.ROOT);
        return lp.endsWith(".password") || lp.endsWith(".token") || lp.endsWith(".secret");
    }

    /**
     * POST /control/config body. {@code op} selects the mutation:
     * <ul>
     *   <li>{@code "set"} (default) — coerce {@code value} to the leaf field's type and set it (scalars/enums).</li>
     *   <li>{@code "put"} — {@code path} is a {@link Map} field; deserialize {@code value} into the map's value type
     *       and {@code map.put(key, …)} (add or replace a keyed entry, e.g. a villager trade or saved trip route).</li>
     *   <li>{@code "add"} — {@code path} is a {@link List} field; deserialize {@code value} into the element type and
     *       append it (e.g. a pearl-stasis location).</li>
     *   <li>{@code "remove"} — drop the {@code key} from a map field, or the {@code index} from a list field.</li>
     * </ul>
     */
    private static final class ControlConfigRequest { String path; String op; String key; Integer index; JsonElement value; }

    /**
     * GET /control/config — the live config tree as JSON, with every secret field redacted (never expose tokens or
     * passwords). The dashboard reads real field values from this to populate the control surface's settings.
     */
    private JsonObject controlConfig() {
        final JsonObject root = GSON.toJsonTree(CONFIG).getAsJsonObject();
        for (final String sp : SECRET_PATHS) {
            redact(root, sp);
        }
        return root;
    }

    private static void redact(final JsonObject root, final String path) {
        final String[] parts = path.split("\\.");
        JsonObject o = root;
        for (int i = 0; i < parts.length - 1 && o != null; i++) {
            final JsonElement el = o.get(parts[i]);
            o = (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
        }
        if (o != null && o.has(parts[parts.length - 1])) {
            o.addProperty(parts[parts.length - 1], "");
        }
    }

    /** POST /control/config {path,value} — set a single config field by dot-path on the live config, then persist. */
    private void handleControlConfigSet(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        if (!CONFIG.server.viewer.control) {
            respondJson(ctx, HttpResponseStatus.FORBIDDEN, "{\"error\":\"control disabled\"}");
            return;
        }
        final String body = req.content().toString(StandardCharsets.UTF_8);
        final ControlConfigRequest r;
        try {
            r = GSON.fromJson(body, ControlConfigRequest.class);
        } catch (final Exception e) {
            respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"bad request\"}");
            return;
        }
        if (r == null || r.path == null || r.path.isBlank()) {
            respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"path required\"}");
            return;
        }
        final String path = r.path.strip();
        if (isSecretPath(path)) {
            respondJson(ctx, HttpResponseStatus.FORBIDDEN, "{\"error\":\"protected field\"}");
            return;
        }
        final String op = (r.op == null || r.op.isBlank()) ? "set" : r.op.strip().toLowerCase(Locale.ROOT);
        // mutate config + persist off the netty thread (mirrors how commands change config)
        EXECUTOR.execute(() -> {
            final Map<String, Object> out;
            switch (op) {
                case "set" -> {
                    if (r.value == null) { respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"value required\"}"); return; }
                    out = setConfigPath(path, r.value);
                }
                case "put", "add", "remove" -> out = mutateContainer(op, path, r.key, r.index, r.value);
                default -> { respondJson(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"unknown op '" + op + "'\"}"); return; }
            }
            respondJson(ctx, HttpResponseStatus.OK, GSON.toJson(out));
        });
    }

    /**
     * Add / replace / remove an entry in a {@link Map}- or {@link List}-typed config field ({@code path} points at the
     * container itself, not a leaf). Entries are deserialized into the container's declared element type with the same
     * {@link com.aquarius.Globals#GSON config Gson} that loads the file, so records ({@code BlockPos}, {@code Route},
     * {@code Pearl}), enums and fastutil maps round-trip exactly as they do on disk. Persists, then nudges any module
     * that caches the list so the change takes effect mid-run.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> mutateContainer(final String op, final String path, final String key,
                                                final Integer index, final JsonElement value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        try {
            final String[] parts = path.split("\\.");
            Object obj = CONFIG;
            Field f = null;
            for (int i = 0; i < parts.length; i++) {
                f = obj.getClass().getField(parts[i]);   // public fields only
                if (i < parts.length - 1) {
                    obj = f.get(obj);
                    if (obj == null) throw new IllegalArgumentException("null parent at '" + parts[i] + "'");
                }
            }
            final Object container = f.get(obj);
            if (container instanceof Map map) {
                switch (op) {
                    case "put" -> {
                        if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
                        if (value == null) throw new IllegalArgumentException("value required");
                        map.put(key, com.aquarius.Globals.GSON.fromJson(value, elementType(f, 1)));
                    }
                    case "remove" -> {
                        if (key == null) throw new IllegalArgumentException("key required");
                        if (map.remove(key) == null) throw new IllegalArgumentException("no entry '" + key + "'");
                    }
                    default -> throw new IllegalArgumentException("op '" + op + "' is not valid on a map");
                }
                out.put("size", map.size());
            } else if (container instanceof List list) {
                switch (op) {
                    case "add" -> {
                        if (value == null) throw new IllegalArgumentException("value required");
                        list.add(com.aquarius.Globals.GSON.fromJson(value, elementType(f, 0)));
                    }
                    case "put" -> {                       // replace the entry at index (in-place edit)
                        if (value == null) throw new IllegalArgumentException("value required");
                        if (index == null || index < 0 || index >= list.size())
                            throw new IllegalArgumentException("index out of range");
                        list.set(index, com.aquarius.Globals.GSON.fromJson(value, elementType(f, 0)));
                    }
                    case "remove" -> {
                        if (index == null || index < 0 || index >= list.size())
                            throw new IllegalArgumentException("index out of range");
                        list.remove((int) index);
                    }
                    default -> throw new IllegalArgumentException("op '" + op + "' is not valid on a list");
                }
                out.put("size", list.size());
            } else {
                throw new IllegalArgumentException(path + " is not a map or list");
            }
            saveConfigAsync();
            notifyConfigConsumers(path);
            SERVER_LOG.info("Control config {}: {} (key={}, index={})", op, path, key, index);
            out.put("ok", true);
            out.put("path", path);
        } catch (final Exception e) {
            out.clear();
            out.put("ok", false);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return out;
    }

    /** The declared element type at generic-argument slot {@code argIndex} of a Map/List field (Object if unparameterized). */
    private static java.lang.reflect.Type elementType(final Field f, final int argIndex) {
        if (f.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
            final java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (argIndex < args.length) return args[argIndex];
        }
        return Object.class;
    }

    /** Best-effort: tell a running module its config list changed so it re-reads instead of using a stale snapshot. */
    private static void notifyConfigConsumers(final String path) {
        try {
            if (path.equals("client.extra.villagerTrader.trades")
                || path.equals("client.extra.villagerTrader.groups")) {
                final var m = MODULE.get(com.aquarius.module.impl.VillagerTrader.class);
                if (m != null) m.onTradeListChange();
            }
        } catch (final Exception ignored) {
            // a notify failure must never fail the write — the change is already persisted
        }
    }

    /** Walk the live config object along {@code a.b.c}, coerce {@code value} to the leaf field's type, set it, save. */
    private Map<String, Object> setConfigPath(final String path, final JsonElement value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        try {
            final String[] parts = path.split("\\.");
            Object obj = CONFIG;
            Field f = null;
            for (int i = 0; i < parts.length; i++) {
                f = obj.getClass().getField(parts[i]);   // public fields only
                if (i < parts.length - 1) {
                    obj = f.get(obj);
                    if (obj == null) throw new IllegalArgumentException("null parent at '" + parts[i] + "'");
                }
            }
            final Object coerced = coerce(f.getType(), value);
            f.set(obj, coerced);
            saveConfigAsync();
            SERVER_LOG.info("Control config set: {} = {}", path, coerced);
            out.put("ok", true);
            out.put("path", path);
            out.put("value", coerced);
        } catch (final Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(final Class<?> t, final JsonElement v) {
        if (t == boolean.class || t == Boolean.class) return v.getAsBoolean();
        if (t == int.class || t == Integer.class) return v.getAsInt();
        if (t == long.class || t == Long.class) return v.getAsLong();
        if (t == double.class || t == Double.class) return v.getAsDouble();
        if (t == float.class || t == Float.class) return v.getAsFloat();
        if (t == String.class) return v.getAsString();
        if (t.isEnum()) return Enum.valueOf((Class<Enum>) t.asSubclass(Enum.class), v.getAsString());
        throw new IllegalArgumentException("unsupported field type: " + t.getSimpleName());
    }

    /** GET /control/state — live module on/off states (for the dashboard's toggles + status). */
    private Map<String, Object> controlState() {
        final Map<String, Object> m = new LinkedHashMap<>();
        final List<Map<String, Object>> mods = new ArrayList<>();
        for (final Module mod : MODULE.getModules()) {
            final Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("name", mod.getClass().getSimpleName());
            mm.put("enabled", mod.isEnabled());
            // modules that expose a richer live status object for their control-plane page
            if (mod instanceof com.aquarius.module.impl.KitMaker km) mm.put("status", km.statusJson());
            mods.add(mm);
        }
        mods.sort(Comparator.comparing(a -> (String) a.get("name")));
        m.put("modules", mods);
        m.put("control", CONFIG.server.viewer.control);
        m.put("t", System.currentTimeMillis());
        return m;
    }

    /** How far the look-target raycast reaches (blocks) — generous so you can aim at a chest across the room. */
    private static final double LOOK_REACH = 96.0;

    /**
     * GET /control/lookingat — raycast the bot's crosshair and return the block it's pointed at: {@code {hit,x,y,z,block}}
     * (or {@code {hit:false}} / {@code {hit:false,offline:true}}). Lets the dashboard fill a chest/coordinate field from
     * wherever the bot is aimed instead of typing x/y/z. Coords are returned to the (loopback/authed-relay) dashboard but
     * never written to the server log.
     */
    private Map<String, Object> lookingAt() {
        final Map<String, Object> m = new LinkedHashMap<>();
        if (!Proxy.getInstance().isConnected()) {   // connected to the server (headless is fine — no client needed)
            m.put("hit", false);
            m.put("offline", true);
            return m;
        }
        BOT.syncFromCache(true);                     // pull the bot's current position + look from the cache, then raycast
        final var r = RaycastHelper.playerBlockRaycast(LOOK_REACH, false);
        m.put("hit", r.hit());
        if (r.hit()) {
            m.put("x", r.x());
            m.put("y", r.y());
            m.put("z", r.z());
            m.put("block", r.block().name());
        }
        return m;
    }

    /** GET /control/commands — the command tree metadata, so the dashboard can auto-generate its palette / forms. */
    private List<Map<String, Object>> commandList() {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Command cmd : COMMAND.getCommands()) {
            final var u = cmd.commandUsage();
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", u.getName());
            m.put("category", u.getCategory() != null ? u.getCategory().name() : "");
            m.put("description", u.getDescription());
            m.put("aliases", u.getAliases());
            m.put("usage", u.getUsageLines());
            out.add(m);
        }
        out.sort(Comparator.comparing(a -> (String) a.get("name")));
        return out;
    }

    /** Renders the bot's surroundings (vanilla map-color indices from {@link MapGenerator}) to a PNG. */
    private static byte[] renderMapPng(final int size) {
        final byte[] data = MapGenerator.generateMapData(size, false); // false = follow the player chunk (smooth), not the vanilla map grid
        final Brightness[] shades = Brightness.values();               // index 0..3 == shade id (LOW/NORMAL/HIGH/LOWEST)
        final byte[] rgb = new byte[size * size * 3];
        for (int i = 0; i < data.length; i++) {
            final int b = data[i] & 0xFF;
            final int baseRgb = MAP_BLOCK_COLOR.getColor(b >> 2);       // packed RGB of the base map color
            final int mod = shades[b & 3].modifier;                    // shade multiplier (180/220/255/135)
            final int o = i * 3;
            rgb[o]     = (byte) ((baseRgb >> 16 & 0xFF) * mod / 255);
            rgb[o + 1] = (byte) ((baseRgb >> 8 & 0xFF) * mod / 255);
            rgb[o + 2] = (byte) ((baseRgb & 0xFF) * mod / 255);
        }
        return PngEncoder.encodeRgb(size, size, rgb);
    }

    private static int intParam(final String uri, final String key, final int def) {
        final int q = uri.indexOf('?');
        if (q < 0) return def;
        for (final String kv : uri.substring(q + 1).split("&")) {
            final int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(key)) {
                try {
                    return Integer.parseInt(kv.substring(eq + 1));
                } catch (final NumberFormatException ignored) {
                    return def;
                }
            }
        }
        return def;
    }

    /** Map side must be a multiple of 16 (whole chunks) within a sane range. */
    private static int clampSize(final int size) {
        final int s = Math.max(64, Math.min(512, size));
        return (s / 16) * 16;
    }

    private void respondJson(final ChannelHandlerContext ctx, final HttpResponseStatus status, final String body) {
        final ByteBuf buf = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
        final FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }

    private void respondPng(final ChannelHandlerContext ctx, final byte[] png, final int centerX, final int centerZ, final int size) {
        final ByteBuf buf = Unpooled.wrappedBuffer(png);
        final FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        // World coord at the image centre + side length (blocks), so the client can pan it precisely (1 block/px).
        res.headers().set("X-Center-X", Integer.toString(centerX));
        res.headers().set("X-Center-Z", Integer.toString(centerZ));
        res.headers().set("X-Size", Integer.toString(size));
        res.headers().set("Access-Control-Expose-Headers", "X-Center-X,X-Center-Z,X-Size");
        res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }

    /** Deflate-compressed binary body (the voxel chunk feed); the client inflates it. */
    private void respondBytes(final ChannelHandlerContext ctx, final byte[] data) {
        final ByteBuf buf = Unpooled.wrappedBuffer(data);
        final FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        res.headers().set("X-Encoding", "deflate");                       // custom: client inflates (not HTTP Content-Encoding)
        res.headers().set("Access-Control-Expose-Headers", "X-Encoding");
        res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
