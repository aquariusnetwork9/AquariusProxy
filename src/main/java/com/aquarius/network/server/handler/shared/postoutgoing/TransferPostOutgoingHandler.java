package com.aquarius.network.server.handler.shared.postoutgoing;

import com.aquarius.network.codec.PostOutgoingPacketHandler;
import com.aquarius.network.server.ServerSession;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;

public class TransferPostOutgoingHandler implements PostOutgoingPacketHandler<ClientboundTransferPacket, ServerSession> {
    @Override
    public void accept(final ClientboundTransferPacket packet, final ServerSession session) {
        session.disconnect(Component.text("Transferring to " + packet.getHost() + ":" + packet.getPort()));
    }
}
