package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.network.UserAuthTask;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ServerboundCookieResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;

import static com.aquarius.Globals.*;

public class SCookieResponseHandler implements PacketHandler<ServerboundCookieResponsePacket, ServerSession> {
    @Override
    public ServerboundCookieResponsePacket apply(final ServerboundCookieResponsePacket packet, final ServerSession session) {
        if (session.isTransferring() && !session.isConfigured()) {
            var cookieKey = packet.getKey();
            session.getCookieCache().handleCookieResponse(cookieKey, packet.getPayload());

            if (session.getCookieCache().receivedAllCookieResponses()) {
                if (CONFIG.server.verifyUsers) {
                    session.send(new ClientboundHelloPacket(session.getServerId(), session.getKeyPair().getPublic(), session.getChallenge(), true));
                } else {
                    EXECUTOR.execute(new UserAuthTask(session, null));
                }
            }
            return null;
        }
        SERVER_LOG.debug("Received unrequested cookie response: {}", packet.getKey());
        // shouldn't ever get here
        // should these packets ever be passed through?
        return null;
    }
}
