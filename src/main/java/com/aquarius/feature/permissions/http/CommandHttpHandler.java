package com.aquarius.feature.permissions.http;

import com.aquarius.command.api.ApiCommandSource;
import com.aquarius.command.api.CommandContext;
import com.aquarius.feature.location.PlayerLocations;
import com.aquarius.feature.permissions.PermissionManager;
import com.aquarius.feature.permissions.Subject;
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
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aquarius.Globals.COMMAND;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.EXECUTOR;
import static com.aquarius.Globals.PERMISSIONS;
import static com.aquarius.Globals.SERVER_LOG;

/**
 * Handles {@code POST /command} for the RBAC HTTP API. Requires an {@code Authorization} token that resolves to a
 * subject; runs the command as that subject (so all RBAC gating applies); returns the command output as JSON.
 * Per-token rate limited; every call is audit-logged. New instance per connection (not shareable).
 */
public class CommandHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Gson GSON = new Gson();
    /** token-hash -> [windowStartMs, count] */
    private static final Map<String, long[]> RATE = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(final ChannelHandlerContext nettyCtx, final FullHttpRequest req) {
        final String path = req.uri().split("\\?", 2)[0];
        final boolean isCommand = path.equals("/command");
        final boolean isPosition = path.equals("/position");
        if (req.method() != HttpMethod.POST || (!isCommand && !isPosition)) {
            respond(nettyCtx, HttpResponseStatus.NOT_FOUND, "{}");
            return;
        }
        final String token = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        final Subject subject = (token == null || token.isBlank()) ? null : PERMISSIONS.resolveToken(token);
        if (subject == null) {
            respond(nettyCtx, HttpResponseStatus.FORBIDDEN, "{}");
            return;
        }
        final String body = req.content().toString(StandardCharsets.UTF_8);

        if (isPosition) {
            // Position telemetry: own (higher) rate budget so it never starves the command budget; attributed to the
            // caller's UUID; not audit-logged (high frequency). The mod reporting this is what lets come/follow target
            // a commander on a separate session the bot can't see.
            if (!allowRate(PermissionManager.sha256Hex(token) + ":pos",
                    Math.max(120, CONFIG.server.permissions.api.requestsPerMinutePerToken))) {
                respond(nettyCtx, HttpResponseStatus.TOO_MANY_REQUESTS, "{}");
                return;
            }
            try {
                final PositionRequest pos = GSON.fromJson(body, PositionRequest.class);
                if (pos == null || subject.uuid() == null) { respond(nettyCtx, HttpResponseStatus.BAD_REQUEST, "{}"); return; }
                PlayerLocations.report(subject.uuid(), pos.x(), pos.y(), pos.z(), pos.dimension());
            } catch (final Exception e) {
                respond(nettyCtx, HttpResponseStatus.BAD_REQUEST, "{}");
                return;
            }
            respond(nettyCtx, HttpResponseStatus.OK, "{\"ok\":true}");
            return;
        }

        // /command
        if (!allowRate(PermissionManager.sha256Hex(token), CONFIG.server.permissions.api.requestsPerMinutePerToken)) {
            respond(nettyCtx, HttpResponseStatus.TOO_MANY_REQUESTS, "{}");
            return;
        }
        String parsed;
        try {
            parsed = GSON.fromJson(body, CommandRequest.class).command();
        } catch (final Exception e) {
            respond(nettyCtx, HttpResponseStatus.BAD_REQUEST, "{}");
            return;
        }
        if (parsed == null || parsed.isBlank()) {
            respond(nettyCtx, HttpResponseStatus.BAD_REQUEST, "{}");
            return;
        }
        final String command = parsed;
        EXECUTOR.execute(() -> respond(nettyCtx, HttpResponseStatus.OK, GSON.toJson(run(subject, command))));
    }

    private CommandResponse run(final Subject subject, final String command) {
        SERVER_LOG.info("API command from {} (role {}): {}", subject.name(), subject.role(), command);
        final CommandContext ctx = CommandContext.create(command, new ApiCommandSource(subject));
        COMMAND.execute(ctx);
        final List<String> lines = new ArrayList<>();
        final var embed = ctx.getEmbed();
        if (embed.title() != null) lines.add(embed.title());
        if (embed.description() != null) lines.add(embed.description());
        for (final var f : embed.fields()) lines.add(f.name() + ": " + f.value());
        lines.addAll(ctx.getMultiLineOutput());
        if (lines.isEmpty()) lines.add("ok");
        return new CommandResponse(embed.title(), null, lines);
    }

    private static synchronized boolean allowRate(final String key, final int perMinute) {
        if (perMinute <= 0) return true;
        final long now = System.currentTimeMillis();
        final long[] w = RATE.computeIfAbsent(key, k -> new long[]{now, 0});
        if (now - w[0] >= 60_000L) {
            w[0] = now;
            w[1] = 0;
        }
        w[1]++;
        return w[1] <= perMinute;
    }

    private static void respond(final ChannelHandlerContext nettyCtx, final HttpResponseStatus status, final String json) {
        final ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        final FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        nettyCtx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext nettyCtx, final Throwable cause) {
        SERVER_LOG.warn("RBAC API handler error", cause);
        nettyCtx.close();
    }
}
