package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.player.PlayerConnectionRemovedEvent;
import com.aquarius.event.player.PlayerLoginEvent;
import com.aquarius.feature.permissions.Subject;
import com.aquarius.module.api.Module;
import com.aquarius.network.codec.PacketHandlerCodec;
import com.aquarius.network.codec.PacketHandlerStateCodec;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.PERMISSIONS;

/**
 * RBAC action enforcement: drops a connected player's chat / movement / interaction packets when their resolved
 * subject lacks {@code action.chat} / {@code action.move} / {@code action.interact}. This is the role→action half
 * of the permission system (the connect-level gating lives in the login handler).
 *
 * <p>Mirrors {@link ActionLimiter}'s pattern (active-predicate codec over a per-session set) but decides from the
 * subject's permissions instead of global config. A no-op by default — every role that can connect (user+) already
 * has movement and chat; this only bites a custom-restricted assignment. Enabled exactly when RBAC is on.
 */
public class RbacGuard extends Module {

    public record Restriction(boolean denyMove, boolean denyChat, boolean denyInteract) {
        boolean any() {
            return denyMove || denyChat || denyInteract;
        }
    }

    private final Map<ServerSession, Restriction> restricted = new ConcurrentHashMap<>();

    @Override
    public boolean enabledSetting() {
        return CONFIG.server.permissions.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PlayerLoginEvent.Pre.class, this::onLogin),
            of(PlayerConnectionRemovedEvent.class, e -> restricted.remove(e.serverConnection()))
        );
    }

    @Override
    public void onDisable() {
        restricted.clear();
    }

    private void onLogin(final PlayerLoginEvent.Pre event) {
        final ServerSession session = event.session();
        final var profile = session.getProfileCache().getProfile();
        if (profile == null) return;
        final Subject subject = PERMISSIONS.resolve(profile.getId(), profile.getName());
        final Restriction r = new Restriction(
            !subject.allows("action.move"),
            !subject.allows("action.chat"),
            !subject.allows("action.interact"));
        if (r.any()) restricted.put(session, r);
    }

    public boolean shouldLimit(final ServerSession session) {
        return restricted.containsKey(session);
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("rbac-guard")
            .setPriority(1100)
            .setActivePredicate(this::shouldLimit)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .inbound(ServerboundChatPacket.class, (p, s) -> denied(s, Restriction::denyChat) ? null : p)
                .inbound(ServerboundChatCommandPacket.class, (p, s) -> denied(s, Restriction::denyChat) ? null : p)
                .inbound(ServerboundMovePlayerPosPacket.class, (p, s) -> denied(s, Restriction::denyMove) ? null : p)
                .inbound(ServerboundMovePlayerPosRotPacket.class, (p, s) -> denied(s, Restriction::denyMove) ? null : p)
                .inbound(ServerboundInteractPacket.class, (p, s) -> denied(s, Restriction::denyInteract) ? null : p)
                .build())
            .build();
    }

    private boolean denied(final ServerSession session, final java.util.function.Predicate<Restriction> which) {
        final Restriction r = restricted.get(session);
        return r != null && which.test(r);
    }
}
