package com.aquarius.feature.viewer;

import com.aquarius.cache.data.chunk.Chunk;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.feature.highways.GriefMap;
import com.aquarius.feature.map.Brightness;
import com.aquarius.feature.map.MapGenerator;
import com.aquarius.module.impl.ElytraPilot;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static com.aquarius.Globals.BLOCK_DATA;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.MAP_BLOCK_COLOR;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.Globals.SERVER_LOG;

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
        if (req.method() != HttpMethod.GET) {
            respondJson(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{}");
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
                final int r = Math.max(8, Math.min(64, intParam(req.uri(), "r", 40)));
                respondBytes(ctx, renderChunks(r, 40, 28));
            } catch (final Exception e) {
                SERVER_LOG.warn("viewer chunks failed", e);
                respondJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"chunks failed\"}");
            }
            return;
        }
        respondJson(ctx, HttpResponseStatus.NOT_FOUND, "{}");
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
