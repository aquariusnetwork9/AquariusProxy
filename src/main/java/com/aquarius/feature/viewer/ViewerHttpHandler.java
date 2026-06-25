package com.aquarius.feature.viewer;

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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

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
                respondPng(ctx, renderMapPng(clampSize(intParam(req.uri(), "size", 256))));
            } catch (final Exception e) {
                SERVER_LOG.warn("viewer map render failed", e);
                respondJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"map render failed\"}");
            }
            return;
        }
        respondJson(ctx, HttpResponseStatus.NOT_FOUND, "{}");
    }

    private Map<String, Object> state() {
        final var pc = CACHE.getPlayerCache();
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", pc.getX());
        m.put("y", pc.getY());
        m.put("z", pc.getZ());
        m.put("yaw", pc.getYaw());
        m.put("pitch", pc.getPitch());
        m.put("health", pc.getThePlayer().getHealth());
        m.put("food", pc.getThePlayer().getFood());
        final ElytraPilot pilot = MODULE.get(ElytraPilot.class);
        m.put("flightPhase", pilot == null ? "IDLE" : pilot.viewerPhase());
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

    private void respondPng(final ChannelHandlerContext ctx, final byte[] png) {
        final ByteBuf buf = Unpooled.wrappedBuffer(png);
        final FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
