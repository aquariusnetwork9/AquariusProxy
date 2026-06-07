package com.aquarius.via;

import io.netty.channel.Channel;
import org.geysermc.mcprotocollib.network.tcp.TcpClientChannelInitializer;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;

import static com.aquarius.Globals.VIA_INITIALIZER;

public class AquariusClientChannelInitializer extends TcpClientChannelInitializer {
    public static final Factory FACTORY = AquariusClientChannelInitializer::new;
    private final TcpClientSession client;

    public AquariusClientChannelInitializer(final TcpClientSession client, final boolean isTransferring) {
        super(client, isTransferring);
        this.client = client;
    }

    @Override
    public void initChannel(final Channel channel) throws Exception {
        super.initChannel(channel);
        VIA_INITIALIZER.clientViaChannelInitializer(channel);
    }
}
