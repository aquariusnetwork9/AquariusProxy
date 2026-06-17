package com.aquarius.feature.bridge;

import com.aquarius.module.impl.Bridge;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

import static com.aquarius.Globals.CONFIG;

/**
 * Terminates the ProxyBridge channel at the proxy: catches the connected player's {@code proxybridge:main}
 * plugin messages (routing them to {@link Bridge} and never forwarding them to the real server), and sniffs
 * {@code minecraft:register} to detect the mod + optionally hide our channel from the real server.
 *
 * <p>Registered via {@link Bridge#registerServerPacketHandlerCodec()} so it only runs while the module is enabled.
 * Returning {@code null} drops a packet (not forwarded upstream); returning a packet forwards it.
 */
public class ServerboundBridgePayloadHandler implements PacketHandler<ServerboundCustomPayloadPacket, ServerSession> {
    private final Bridge bridge;

    public ServerboundBridgePayloadHandler(final Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public ServerboundCustomPayloadPacket apply(final ServerboundCustomPayloadPacket packet, final ServerSession session) {
        final Key channel = packet.getChannel();

        // our private bridge channel — consume, never forward to the destination server
        if (channel.namespace().equals(BridgeProtocol.CHANNEL_NAMESPACE)
            && channel.value().equals(BridgeProtocol.CHANNEL_PATH)) {
            bridge.handleInbound(session, packet.getData());
            return null;
        }

        // channel announcement — detect the mod, and optionally strip our channel so the server never sees it
        if (channel.namespace().equals("minecraft") && channel.value().equals("register")) {
            final byte[] data = packet.getData();
            if (BridgeProtocol.registerContainsChannel(data, BridgeProtocol.CHANNEL_ID)) {
                bridge.markModPresent(session);
                if (CONFIG.client.extra.bridge.stripRegisterChannel) {
                    final byte[] stripped = BridgeProtocol.stripChannelFromRegister(data, BridgeProtocol.CHANNEL_ID);
                    if (stripped != null) {
                        if (stripped.length == 0) return null; // nothing left to announce
                        return new ServerboundCustomPayloadPacket(channel, stripped);
                    }
                }
            }
        }

        return packet;
    }
}
