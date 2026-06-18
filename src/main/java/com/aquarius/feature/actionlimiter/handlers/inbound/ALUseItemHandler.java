package com.aquarius.feature.actionlimiter.handlers.inbound;

import com.aquarius.module.impl.ActionLimiter;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class ALUseItemHandler implements PacketHandler<ServerboundUseItemPacket, ServerSession> {
    @Override
    public ServerboundUseItemPacket apply(final ServerboundUseItemPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.allowUseItem) return null;
        // Blacklisted items may be held but not used.
        if (CONFIG.client.extra.actionLimiter.itemsBlacklistEnabled
            && MODULE.get(ActionLimiter.class).isBlacklistedItem(CACHE.getPlayerCache().getEquipment(packet.getHand()))) {
            return null;
        }
        return packet;
    }
}
