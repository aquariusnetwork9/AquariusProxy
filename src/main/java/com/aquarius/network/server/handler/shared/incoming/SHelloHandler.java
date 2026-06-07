package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.network.UserAuthTask;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import com.aquarius.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.EXECUTOR;

public class SHelloHandler implements PacketHandler<ServerboundHelloPacket, ServerSession> {
    @Override
    public ServerboundHelloPacket apply(@NonNull ServerboundHelloPacket packet, @NonNull ServerSession session) {
        if (!ChatUtil.isValidPlayerName(packet.getUsername())) {
            session.disconnect("Invalid username.");
            return null;
        }
        session.setUsername(packet.getUsername());
        session.setLoginProfileUUID(packet.getProfileId());
        if (session.isTransferring())
            // TODO: see how viaversion interacts with this sequence
            //  it seems to be legal for clients to not send a response to the cookie request, at which point we stall
            //  in this login sequence forever
            session.getCookieCache().getPackets(session::sendAsync, session);
        else {
            if (CONFIG.server.verifyUsers)
                session.sendAsync(new ClientboundHelloPacket(session.getServerId(), session.getKeyPair().getPublic(), session.getChallenge(), true));
            else
                EXECUTOR.execute(new UserAuthTask(session, null));
        }
        return null;
    }
}
