package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.player.PlayerConnectionRemovedEvent;
import com.zenith.event.player.PlayerLoginEvent;
import com.zenith.feature.actionlimiter.handlers.inbound.*;
import com.zenith.feature.actionlimiter.handlers.outbound.ALCMoveVehicleHandler;
import com.zenith.feature.actionlimiter.handlers.outbound.ALLoginHandler;
import com.zenith.feature.actionlimiter.handlers.outbound.ALPlayerPositionHandler;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundEditBookPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class ActionLimiter extends Module {
    private final ReferenceSet<ServerSession> limitedConnections = new ReferenceOpenHashSet<>();

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("action-limiter")
            .setPriority(1000)
            .setActivePredicate(this::shouldLimit)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .inbound(ServerboundChatCommandPacket.class, new ALChatCommandHandler())
                .inbound(ServerboundChatCommandSignedPacket.class, new ALSignedChatCommandHandler())
                .inbound(ServerboundChatPacket.class, new ALChatHandler())
                .inbound(ServerboundClientCommandPacket.class, new ALClientCommandHandler())
                .inbound(ServerboundContainerClickPacket.class, new ALContainerClickHandler())
                .inbound(ServerboundInteractPacket.class, new ALInteractHandler())
                .inbound(ServerboundMovePlayerPosPacket.class, new ALMovePlayerPosHandler())
                .inbound(ServerboundMovePlayerPosRotPacket.class, new ALMovePlayerPosRotHandler())
                .inbound(ServerboundMoveVehiclePacket.class, new ALMoveVehicleHandler())
                .inbound(ServerboundPlayerActionPacket.class, new ALPlayerActionHandler())
                .inbound(ServerboundUseItemOnPacket.class, new ALUseItemOnHandler())
                .inbound(ServerboundUseItemPacket.class, new ALUseItemHandler())
                .inbound(ServerboundEditBookPacket.class, new ALEditBookHandler())
                .outbound(ClientboundMoveVehiclePacket.class, new ALCMoveVehicleHandler())
                .outbound(ClientboundLoginPacket.class, new ALLoginHandler())
                .outbound(ClientboundPlayerPositionPacket.class, new ALPlayerPositionHandler())
                .build())
            .build();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PlayerLoginEvent.Pre.class, this::onPlayerLoginEvent),
            of(PlayerConnectionRemovedEvent.class, this::onServerConnectionRemoved)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.actionLimiter.enabled;
    }

    public void onPlayerLoginEvent(final PlayerLoginEvent.Pre event) {
        ServerSession session = event.session();
        if (CONFIG.client.extra.actionLimiter.exemptProxyAccount) {
            var profile = session.getProfileCache().getProfile();
            var proxyProfile = CACHE.getProfileCache().getProfile();
            if (profile != null && proxyProfile != null && profile.getId().equals(proxyProfile.getId())) {
                // exempt by not adding to limited connection set
            } else {
                limitedConnections.add(session);
            }
        } else {
            limitedConnections.add(session);
        }
        if (!CONFIG.client.extra.actionLimiter.allowMovement) {
            if (CACHE.getPlayerCache().getY() <= CONFIG.client.extra.actionLimiter.movementMinY) {
                info("Below home min Y");
                disconnect(session, "Movement not allowed");
            } else if (MathHelper.distance2d(
                CONFIG.client.extra.actionLimiter.movementHomeX,
                CONFIG.client.extra.actionLimiter.movementHomeZ,
                CACHE.getPlayerCache().getX(),
                CACHE.getPlayerCache().getZ()
            ) > CONFIG.client.extra.actionLimiter.movementDistance
            ) {
                info("Too far from home");
                disconnect(session, "Movement not allowed");
            }
        }
    }

    public void onServerConnectionRemoved(final PlayerConnectionRemovedEvent event) {
        limitedConnections.remove(event.serverConnection());
    }

    public boolean shouldLimit(final ServerSession serverConnection) {
        return limitedConnections.contains(serverConnection);
    }
}
