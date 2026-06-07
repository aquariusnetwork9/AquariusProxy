package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.event.player.PlayerConfigurationEvent;
import com.aquarius.network.SKeepAliveTask;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import com.aquarius.network.server.handler.ProxyServerLoginHandler;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;

import java.util.concurrent.TimeUnit;

import static com.aquarius.Globals.EVENT_BUS;

public class FinishConfigurationHandler implements PacketHandler<ServerboundFinishConfigurationPacket, ServerSession> {
    @Override
    public ServerboundFinishConfigurationPacket apply(final ServerboundFinishConfigurationPacket packet, final ServerSession session) {
        session.switchInboundState(ProtocolState.GAME);
        session.setClientLoaded(false);
        if (!session.isConfigured()) {
            ProxyServerLoginHandler.INSTANCE.loggedIn(session);
            session.getEventLoop().scheduleAtFixedRate(new SKeepAliveTask(session), 0, 2, TimeUnit.SECONDS);
            return null;
        }
        EVENT_BUS.post(new PlayerConfigurationEvent.Exited(session));
        return packet;
    }
}
