package com.aquarius.via;

import io.netty.channel.Channel;
import org.geysermc.mcprotocollib.network.tcp.TcpServer;
import org.geysermc.mcprotocollib.network.tcp.TcpServerChannelInitializer;

import static com.aquarius.Globals.VIA_INITIALIZER;

public class AquariusServerChannelInitializer extends TcpServerChannelInitializer {
    public static final Factory FACTORY = AquariusServerChannelInitializer::new;

    public AquariusServerChannelInitializer(final TcpServer server) {
        super(server);
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        super.initChannel(channel);
        VIA_INITIALIZER.serverViaChannelInitializer(channel);
    }
}
