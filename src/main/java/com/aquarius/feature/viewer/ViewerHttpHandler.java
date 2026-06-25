package com.aquarius.feature.viewer;

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
        m.put("t", System.currentTimeMillis());
        return m;
    }

    private void respondJson(final ChannelHandlerContext ctx, final HttpResponseStatus status, final String body) {
        final ByteBuf buf = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
        final FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
