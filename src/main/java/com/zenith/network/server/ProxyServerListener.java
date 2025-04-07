package com.zenith.network.server;

import com.zenith.api.event.player.PlayerConnectionAddedEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.geysermc.mcprotocollib.network.event.server.*;

import static com.zenith.Globals.*;


@RequiredArgsConstructor
@Getter
public class ProxyServerListener implements ServerListener {
    @Override
    public void serverBound(ServerBoundEvent event) {
        SERVER_LOG.info("Server started.");
    }

    @Override
    public void serverClosing(ServerClosingEvent event) {
        SERVER_LOG.info("Closing server...");
    }

    @Override
    public void serverClosed(ServerClosedEvent event) {
        SERVER_LOG.info("Server closed.");
    }

    @Override
    public void sessionAdded(SessionAddedEvent event) {
        ServerSession connection = (ServerSession) event.getSession();
        if (CONFIG.server.extra.timeout.enable)
            connection.setReadTimeout(CONFIG.server.extra.timeout.seconds);
        else
            connection.setReadTimeout(0);
        EVENT_BUS.post(new PlayerConnectionAddedEvent(connection));
    }

    @Override
    public void sessionRemoved(SessionRemovedEvent event) {}
}
