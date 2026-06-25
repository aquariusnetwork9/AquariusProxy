package com.aquarius.feature.viewer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import static com.aquarius.Globals.SERVER_LOG;

/** Minimal netty HTTP server hosting the read-only viewer feed. Loopback-bound by default (see {@link ViewerConfig}). */
public final class ViewerHttpServer {
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public synchronized void start(final String host, final int port) {
        if (channel != null) return;
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            final ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1 << 16)); // 64 KiB max request
                        ch.pipeline().addLast(new ViewerHttpHandler());
                    }
                });
            channel = b.bind(host, port).sync().channel();
            SERVER_LOG.info("Viewer API listening on {}:{}", host, port);
        } catch (final Exception e) {
            SERVER_LOG.error("Failed to start Viewer API on {}:{}", host, port, e);
            stop();
        }
    }

    public synchronized void stop() {
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
                bossGroup = null;
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
                workerGroup = null;
            }
        }
    }
}
