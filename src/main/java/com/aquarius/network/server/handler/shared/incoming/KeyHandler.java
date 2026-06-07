package com.aquarius.network.server.handler.shared.incoming;

import com.aquarius.network.UserAuthTask;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundKeyPacket;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.util.Arrays;

import static com.aquarius.Globals.EXECUTOR;

public class KeyHandler implements PacketHandler<ServerboundKeyPacket, ServerSession> {

    @Override
    public ServerboundKeyPacket apply(final ServerboundKeyPacket packet, final ServerSession session) {
        PrivateKey privateKey = session.getKeyPair().getPrivate();
        if (!Arrays.equals(session.getChallenge(), packet.getEncryptedChallenge(privateKey))) {
            session.disconnect("Invalid challenge!");
            return null;
        }
        SecretKey key = packet.getSecretKey(privateKey);
        session.enableEncryption(key);
        EXECUTOR.execute(new UserAuthTask(session, key));
        return null;
    }
}
