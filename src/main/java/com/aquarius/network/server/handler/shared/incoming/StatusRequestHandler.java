package com.aquarius.network.server.handler.shared.incoming;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import com.aquarius.network.server.AquariusServerInfoBuilder;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.geysermc.mcprotocollib.protocol.packet.status.clientbound.ClientboundStatusResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.status.serverbound.ServerboundStatusRequestPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.SERVER_LOG;

public class StatusRequestHandler implements PacketHandler<ServerboundStatusRequestPacket, ServerSession> {
    @Override
    public ServerboundStatusRequestPacket apply(final ServerboundStatusRequestPacket packet, final ServerSession session) {
        if (CONFIG.server.ping.logPings)
            SERVER_LOG.info("[Ping] Request from: {} [{}] to: {}:{}",
                            session.getRemoteAddress(),
                            ProtocolVersion.getProtocol(session.getProtocolVersionId()).getName(),
                            session.getConnectingServerAddress(),
                            session.getConnectingServerPort());
        ServerStatusInfo info = AquariusServerInfoBuilder.INSTANCE.buildInfo(session);
        if (info == null) session.disconnect("bye");
        else session.send(new ClientboundStatusResponsePacket(info));
        return null;
    }
}
