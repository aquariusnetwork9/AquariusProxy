package com.aquarius.network;

import com.aquarius.network.server.ServerSession;
import com.aquarius.util.config.Config.Client.KeepAliveHandling.KeepAliveMode;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;

import static com.aquarius.Globals.CONFIG;

public class SKeepAliveTask implements Runnable {
    private final ServerSession session;

    public SKeepAliveTask(ServerSession session) {
        this.session = session;
    }

    @Override
    public void run() {
        if (!session.isSpectator() && CONFIG.client.keepAliveHandling.keepAliveMode != KeepAliveMode.INDEPENDENT) return;
        var state = session.getPacketProtocol().getOutboundState();
        if (state != ProtocolState.CONFIGURATION && state != ProtocolState.GAME) return;
        this.session.send(new ClientboundKeepAlivePacket(System.currentTimeMillis()));
    }
}
