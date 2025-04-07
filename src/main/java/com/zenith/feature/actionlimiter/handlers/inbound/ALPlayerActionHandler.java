package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

import static com.zenith.Globals.CONFIG;

public class ALPlayerActionHandler implements PacketHandler<ServerboundPlayerActionPacket, ServerSession> {
    @Override
    public ServerboundPlayerActionPacket apply(final ServerboundPlayerActionPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.allowBlockBreaking && (
            packet.getAction() == PlayerAction.START_DESTROY_BLOCK
            || packet.getAction() == PlayerAction.STOP_DESTROY_BLOCK
            || packet.getAction() == PlayerAction.ABORT_DESTROY_BLOCK
        )) {
            // todo: force client to cancel digging and send block update packet
            return null;
        }
        if (!CONFIG.client.extra.actionLimiter.allowInventory && (
            packet.getAction() == PlayerAction.DROP_ITEM
            || packet.getAction() == PlayerAction.DROP_ALL_ITEMS
            || packet.getAction() == PlayerAction.SWAP_ITEM_WITH_OFFHAND
        )) {
            return null;
        }
        return packet;
    }
}
