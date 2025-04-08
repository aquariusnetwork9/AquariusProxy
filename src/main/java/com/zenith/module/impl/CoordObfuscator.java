package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.api.event.player.PlayerConnectionRemovedEvent;
import com.zenith.api.event.player.PlayerLoginEvent;
import com.zenith.api.module.Module;
import com.zenith.api.network.PacketCodecRegistries;
import com.zenith.api.network.PacketHandlerCodec;
import com.zenith.api.network.PacketHandlerStateCodec;
import com.zenith.api.network.server.ServerSession;
import com.zenith.cache.data.PlayerCache;
import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.feature.coordobf.ObfPlayerState;
import com.zenith.feature.coordobf.handlers.inbound.*;
import com.zenith.feature.coordobf.handlers.outbound.*;
import com.zenith.feature.player.World;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.util.ComponentSerializer;
import com.zenith.util.Config.Client.Extra.CoordObfuscation.ObfuscationMode;
import com.zenith.util.TickTimerManager;
import com.zenith.util.Wait;
import com.zenith.util.math.MathHelper;
import com.zenith.util.math.MutableVec3d;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerLookAtPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

// todo: delay player logins until teleport queue is empty, there's some race condition on dimension switch transfers where a tp is lost
// todo: better way to determine if we should replace bedrock layer in a dimension. dimension registry is not guaranteed to be sync'd to server's dim registry
public class CoordObfuscator extends Module {
    private final Random random = new SecureRandom();
    private final Map<ServerSession, ObfPlayerState> playerStateMap = new ConcurrentHashMap<>();
    @Getter
    private final MutableVec3d serverTeleportPos = new MutableVec3d(0.0, 0.0, 0.0);
    private final MutableVec3d preTeleportClientPos = new MutableVec3d(0.0, 0.0, 0.0);
    @Getter @Setter
    private boolean nextPlayerMovePacketIsTeleport = false;

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(PlayerConnectionRemovedEvent.class, this::onServerConnectionRemoved),
            of(PlayerLoginEvent.Pre.class, this::onPlayerLoginEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.coordObfuscation.enabled;
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("coord-obf")
            .setPriority(Integer.MAX_VALUE-1) // 1 less than packet logger
            .setActivePredicate(this::shouldObfuscateSession)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .registerInbound(ServerboundAcceptTeleportationPacket.class, new COAcceptTeleportationHandler())
                .registerInbound(ServerboundMoveVehiclePacket.class, new COSMoveVehicleHandler())
                .registerInbound(ServerboundPlayerActionPacket.class, new COPlayerActionHandler())
                .registerInbound(ServerboundMovePlayerPosPacket.class, new COMovePlayerPosHandler())
                .registerInbound(ServerboundMovePlayerPosRotPacket.class, new COMovePlayerPosRotHandler())
                .registerInbound(ServerboundSignUpdatePacket.class, new COSignUpdateHandler())
                .registerInbound(ServerboundUseItemPacket.class, new COUseItemHandler())
                .registerInbound(ServerboundUseItemOnPacket.class, new COUseItemOnHandler())
                .registerOutbound(ClientboundAddEntityPacket.class, new COAddEntityHandler())
                .registerOutbound(ClientboundAddExperienceOrbPacket.class, new COAddExperienceOrbHandler())
                .registerOutbound(ClientboundBlockDestructionPacket.class, new COBlockDestructionHandler())
                .registerOutbound(ClientboundBlockEntityDataPacket.class, new COBlockEntityDataHandler())
                .registerOutbound(ClientboundBlockEventPacket.class, new COBlockEventHandler())
                .registerOutbound(ClientboundBlockUpdatePacket.class, new COBlockUpdateHandler())
                .registerOutbound(ClientboundChunksBiomesPacket.class, new COChunksBiomesHandler())
                .registerOutbound(ClientboundContainerSetContentPacket.class, new COContainerSetContentHandler())
                .registerOutbound(ClientboundContainerSetSlotPacket.class, new COContainerSetSlotHandler())
                .registerOutbound(ClientboundDamageEventPacket.class, new CODamageEventHandler())
                .registerOutbound(ClientboundExplodePacket.class, new COExplodeHandler())
                .registerOutbound(ClientboundForgetLevelChunkPacket.class, new COForgetLevelChunkHandler())
                .registerOutbound(ClientboundLevelChunkWithLightPacket.class, new COLevelChunkWithLightHandler())
                .registerOutbound(ClientboundLevelEventPacket.class, new COLevelEventHandler())
                .registerOutbound(ClientboundLevelParticlesPacket.class, new COLevelParticlesHandler())
                .registerOutbound(ClientboundLightUpdatePacket.class, new COLightUpdateHandler())
                .registerOutbound(ClientboundLoginPacket.class, new COLoginHandler())
                .registerOutbound(ClientboundMoveEntityPosPacket.class, new COMoveEntityPosHandler())
                .registerOutbound(ClientboundMoveEntityPosRotPacket.class, new COMoveEntityPosRotHandler())
                .registerOutbound(ClientboundMoveEntityRotPacket.class, new COMoveEntityRotHandler())
                .registerOutbound(ClientboundMoveVehiclePacket.class, new COCMoveVehicleHandler())
                .registerOutbound(ClientboundOpenSignEditorPacket.class, new COOpenSignEditorHandler())
                .registerOutbound(ClientboundPlayerLookAtPacket.class, new COPlayerLookAtHandler())
                .registerOutbound(ClientboundPlayerPositionPacket.class, new COPlayerPositionHandler())
                .registerOutbound(ClientboundRespawnPacket.class, new CORespawnHandler())
                .registerOutbound(ClientboundSectionBlocksUpdatePacket.class, new COSectionBlocksUpdateHandler())
                .registerOutbound(ClientboundSetChunkCacheCenterPacket.class, new COSetChunkCacheCenterHandler())
                .registerOutbound(ClientboundSetDefaultSpawnPositionPacket.class, new COSetDefaultSpawnPositionHandler())
                .registerOutbound(ClientboundSetEntityDataPacket.class, new COSetEntityDataHandler())
                .registerOutbound(ClientboundSetEntityMotionPacket.class, new COSetEntityMotionHandler())
                .registerOutbound(ClientboundSetEquipmentPacket.class, new COSetEquipmentHandler())
                .registerOutbound(ClientboundSoundPacket.class, new COSoundHandler())
                .registerOutbound(ClientboundTagQueryPacket.class, new COTagQueryHandler())
                .registerOutbound(ClientboundTeleportEntityPacket.class, new COTeleportEntityHandler())
                .build())
            .build();
    }

    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("coord-obf-client")
            .setPriority(Integer.MAX_VALUE-1)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .registerInbound(ClientboundPlayerPositionPacket.class, (packet, session) -> {
                    // by the time our server handler for teleports is called, we may have already updated the player cache pos
                    // subject to race conditions, as cache update is done on the client event loop
                    PlayerCache cache = CACHE.getPlayerCache();
                    preTeleportClientPos.set(cache.getX(), cache.getY(), cache.getZ());
                    return packet;
                })
                .build())
            .build();
    }
    AtomicLong awaitLoginsUntil = new AtomicLong(0);
    PacketHandlerCodec loginSlowDownPacketHandlerCodec = PacketHandlerCodec.serverBuilder()
        .setId("coord-obf-rate-limit")
        .setPriority(Integer.MAX_VALUE-1)
        .state(ProtocolState.LOGIN, PacketHandlerStateCodec.serverBuilder()
            .registerInbound(ServerboundLoginAcknowledgedPacket.class, (packet, session) -> {
                if(Wait.waitUntil(() -> awaitLoginsUntil.get() < System.currentTimeMillis(), 5)) {
                    return packet;
                }
                session.disconnect("[Coord Obfuscation] Login took too long");
                return packet;
            })
            .build())
        .build();

    @Override
    public void onEnable() {
        reconnectAllActiveConnections();
        PacketCodecRegistries.SERVER_REGISTRY.register(loginSlowDownPacketHandlerCodec);
    }

    @Override
    public void onDisable() {
        PacketCodecRegistries.SERVER_REGISTRY.unregister(loginSlowDownPacketHandlerCodec);
        reconnectAllActiveConnections();
        playerStateMap.clear();
    }

    public ObfPlayerState getPlayerState(ServerSession session) {
        var state = playerStateMap.get(session);
        if (state == null) {
            if (!session.isDisconnected()) error("Tried to get player state for {} but it was null", session.getProfileCache().getProfile().getName(), new Exception(""));
            disconnect(session, "Invalid state");
            return new ObfPlayerState(session);
        }
        return state;
    }

    public CoordOffset getCoordOffset(ServerSession session) {
        return getPlayerState(session).getCoordOffset();
    }

    public void onPlayerLoginEvent(final PlayerLoginEvent.Pre event) {
        try {
            if (!Proxy.getInstance().isConnected()) {
                disconnect(event.session(), "Disconnected");
                return;
            }
            if (Proxy.getInstance().getClient().isInQueue() || !Proxy.getInstance().getClient().isOnline()) {
                info("Disconnecting {} as we are in queue", event.session().getProfileCache().getProfile().getName());
                disconnect(event.session(), "Queueing");
                return;
            }
            awaitNextClientTick(event.session());
            if (event.session().isDisconnected()) return;
            if (CACHE.getPlayerCache().isRespawning()) {
                info("Reconnecting {} due to respawn in progress", event.session().getProfileCache().getProfile().getName());
                reconnect(event.session());
                return;
            }
            if (Proxy.getInstance().getClient().isInQueue()) {
                info("Disconnecting {} as we are in queue", event.session().getProfileCache().getProfile().getName());
                disconnect(event.session(), "Queueing");
                return;
            }
            if (CACHE.getChunkCache().getCache().size() < 24) {
                info("Reconnecting {} due to chunk cache not being populated", event.session().getProfileCache().getProfile().getName());
                reconnect(event.session());
                return;
            }
            ServerSession serverConnection = event.session();
            var profile = serverConnection.getProfileCache().getProfile();
            var proxyProfile = CACHE.getProfileCache().getProfile();
            if (CONFIG.client.extra.coordObfuscation.exemptProxyAccount && profile != null && proxyProfile != null && profile.getId().equals(proxyProfile.getId())) {
                info("Exempted proxy account session with no offset: {}", profile.getName());
                return;
            }
            var state = new ObfPlayerState(serverConnection);
            playerStateMap.put(serverConnection, state);
            var playerPos = state.getPlayerPos();
            var coordOffset = generateOffset(event.session(), playerPos.getX(), playerPos.getZ());
            state.setCoordOffset(coordOffset);
            info("Offset for {}: {}, {}", profile.getName(), coordOffset.x(), coordOffset.z());
        } catch (final Exception e) {
            error("Failed to generate coord offset", e);
            disconnect(event.session(), "bye");
        }
    }

    public void onServerConnectionRemoved(final PlayerConnectionRemovedEvent event) {
        playerStateMap.remove(event.serverConnection());
    }

    public void onConfigChange() {
        reconnectAllActiveConnections();
    }

    private CoordOffset generateOffset(ServerSession session, double playerX, double playerZ) {
        return switch (CONFIG.client.extra.coordObfuscation.mode) {
            case ObfuscationMode.RANDOM_OFFSET -> generateRandomOffset(playerX, playerZ);
            case ObfuscationMode.CONSTANT_OFFSET -> generateConstantOffset(session, playerX, playerZ);
            case ObfuscationMode.AT_LOCATION -> generateLocationOffset(playerX, playerZ);
        };
    }

    private CoordOffset generateRandomOffset(final double playerX, final double playerZ) {
        int x, z;
        int tries = 0;
        while (true) {
            if (tries++ > 100) {
                throw new RuntimeException("Failed to generate coord offset after 100 tries lol");
            }
            x = generateRandomOffsetVal();
            z = generateRandomOffsetVal();
            int xOffsetRes = (int) (playerX + x);
            if (Math.abs(xOffsetRes) > 29000000)
                continue;
            int zOffsetRes = (int) (playerZ + z);
            if (Math.abs(zOffsetRes) > 29000000)
                continue;
            if (MathHelper.distance2d(0, 0, xOffsetRes, zOffsetRes) < CONFIG.client.extra.coordObfuscation.randomMinSpawnDistance)
                continue;
            break;
        }
        return new CoordOffset(x / 16, z / 16);
    }

    private CoordOffset generateConstantOffset(final ServerSession session, final double playerX, final double playerZ) {
        if (MathHelper.distance2d(0, 0, playerX, playerZ) < CONFIG.client.extra.coordObfuscation.constantOffsetMinSpawnDistance) {
            info("Disconnecting {} as we are to too close to spawn", session.getProfileCache().getProfile().getName());
            disconnect(session, "bye");
            return new CoordOffset(random.nextInt(12345, 999999), random.nextInt(12345, 999999));
        }

        if (CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate) {
            DimensionData dimension = World.getCurrentDimension();
            if (dimension.id() == DimensionRegistry.THE_NETHER.id()) {
                return new CoordOffset((CONFIG.client.extra.coordObfuscation.constantOffsetX / 16) / 8, (CONFIG.client.extra.coordObfuscation.constantOffsetZ / 16) / 8);
            }
        }
        return new CoordOffset(CONFIG.client.extra.coordObfuscation.constantOffsetX / 16, CONFIG.client.extra.coordObfuscation.constantOffsetZ / 16);
    }

    private CoordOffset generateLocationOffset(final double playerX, final double playerZ) {
        int playerChunkX = (int) Math.floor(playerX / 16);
        int playerChunkZ = (int) Math.floor(playerZ / 16);
        int xOffset = (CONFIG.client.extra.coordObfuscation.atLocationX / 16) - playerChunkX;
        int zOffset = (CONFIG.client.extra.coordObfuscation.atLocationZ / 16) - playerChunkZ;
        return new CoordOffset(xOffset, zOffset);
    }

    private int generateRandomOffsetVal() {
        return random.nextInt(
            CONFIG.client.extra.coordObfuscation.randomMinOffset,
            CONFIG.client.extra.coordObfuscation.randomMinOffset + CONFIG.client.extra.coordObfuscation.randomBound)
            * (random.nextBoolean() ? 1 : -1);
    }

    public void playerMovePos(final ServerSession session, final double x, final double z) {
        var state = getPlayerState(session);
        MutableVec3d pos = state.getPlayerPos();
        if (!session.isSpectator()) {
            if (MathHelper.distance2d(x, z, pos.getX(), pos.getZ()) > CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin) {
                info("Reconnecting {} due to long distance movement", session.getProfileCache().getProfile().getName());
                reconnect(session);
                return;
            }
            var playerMoveDist = MathHelper.distance2d(x, z, CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getZ());
            if (playerMoveDist > CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin) {
                info("Reconnecting {} due to long distance movement", session.getProfileCache().getProfile().getName());
                reconnect(session);
                return;
            }
        }
        pos.setX(x);
        pos.setZ(z);
    }

    public void setPlayerAcceptTeleport(final ServerSession session, final int id) {
        if (session.isSpectator()) return; // ignore for spectators
        // next player move packet needs to align with server's coords exactly
        this.setNextPlayerMovePacketIsTeleport(true);
    }

    public void setServerTeleportPos(final ServerSession session, final double x, final double y, final double z, final int teleportId) {
        if (session.isSpectator()) return; // ignore for spectators
        this.serverTeleportPos.set(x, y, z);
    }

    public void awaitNextClientTick(ServerSession session) {
        try {
            var client = Proxy.getInstance().getClient();
            if (client.isDisconnected()) throw new RuntimeException("Client is disconnected");
            if (client.getClientEventLoop().inEventLoop()) {
                throw new RuntimeException("Cannot await next client tick from client tick event loop");
            }
            var tickTime = TickTimerManager.INSTANCE.getTickTime();
            var clientEventLoop = client.getClientEventLoop();
            while (true) {
                // await any remaining tasks in the event loop
                if (clientEventLoop.isShuttingDown()) {
                    throw new RuntimeException("Client event loop is shutting down");
                }
                if (!client.isOnline()) { // client does not tick unless its online (not in queue)
                    throw new RuntimeException("Client is not online");
                }
                clientEventLoop.submit(() -> {}).get();
                if (TickTimerManager.INSTANCE.getTickTime() - tickTime > 1)
                    break;
                Wait.waitMs(50);
            }

        } catch (Exception e) {
            error("Failed to await next client tick", e);
            disconnect(session, "bye");
        }
    }

    public void onServerTeleport(final ServerSession session, double x, double y, double z, final int teleportId, final List<PositionElement> relative) {
        if (teleportId == session.getSpawnTeleportId() && !session.isSpawned()) return;
        if (relative.contains(PositionElement.X)) {
            x += preTeleportClientPos.getX();
        }
        if (relative.contains(PositionElement.Y)) {
            y += preTeleportClientPos.getY();
        }
        if (relative.contains(PositionElement.Z)) {
            z += preTeleportClientPos.getZ();
        }
        setServerTeleportPos(session, x, y, z, teleportId);
        if (session.isRespawning()) {
            info("Reconnecting {} due to teleport during respawn", session.getProfileCache().getProfile().getName());
            reconnect(session);
//            var futureOffset = generateOffset(session, x, z);
//            info("Regenerated offset due to respawn teleport: {} {}", futureOffset.x(), futureOffset.z());
//            getPlayerState(session).setCoordOffset(futureOffset);
//            session.setRespawning(false);
            return;
        }

        if (MathHelper.distance2d(x, z, preTeleportClientPos.getX(), preTeleportClientPos.getZ()) >= CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin) {
            info("Reconnecting {} due to long distance teleport using preTeleportPos calc", session.getProfileCache().getProfile().getName());
            reconnect(session);
            return;
        }
        // it is possible for controlling players to manipulate the player cache position temporarily
        // but chunk cache should be not be able to be manipulated
        if (MathHelper.distance2d(x / 16, z / 16, CACHE.getChunkCache().getCenterX(), CACHE.getChunkCache().getCenterZ()) >= CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin / 16.0) {
            info("Reconnecting {} due to long distance teleport using chunk center calc", session.getProfileCache().getProfile().getName());
            reconnect(session);
            return;
        }
    }

    public void reconnect(ServerSession session) {
        awaitLoginsUntil.set(System.currentTimeMillis() + 1000);
        if (session.isSpectator()) {
            session.transferToSpectator();
        } else {
            session.transferToControllingPlayer();
        }
    }

    public void onRespawn(final ServerSession session, final int dimension) {
        session.setRespawning(true);
        // todo: could be possible to avoid a reconnect here
        //  needs more testing
        reconnect(session);
    }

    private void reconnectAllActiveConnections() {
        var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            var con = connections[i];
            reconnect(con);
        }
    }

    public boolean shouldObfuscateSession(ServerSession session) {
        return playerStateMap.containsKey(session);
    }

    public void disconnect(ServerSession session, String reason) {
        session.disconnect(ComponentSerializer.minimessage("<red>[Coordinate Obfuscation]</red> <gray>" + reason));
    }

    public record ValidationResult(boolean valid, List<String> invalidReasons) {}

    public ValidationResult validateSetup() {
        // check all modules and settings that could lead to leaking coordinates or the offset

        boolean valid = true;
        List<String> invalidReasons = new ArrayList<>();

        // ingame commands, both sending/receiving
        if (CONFIG.inGameCommands.enable) {
            invalidReasons.add("In-game commands should be disabled, many commands leak coordinates in outputs and behavior: `commandConfig ingame off`");
            valid = false;
        }
        if (!CONFIG.client.extra.actionLimiter.enabled) {
            invalidReasons.add("Action Limiter should be enabled to prevent long distance movement and respawning: `actionLimiter on`");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowMovement) {
            invalidReasons.add("Action Limiter movement should be disabled to prevent long distance movement: `actionLimiter allowMovement off`");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowRespawn) {
            invalidReasons.add("Action Limiter respawns should be disabled to prevent respawning: `actionLimiter allowRespawn off`");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowChat) {
            invalidReasons.add("Action Limiter chat should be disabled to prevent you getting muted or AntiLeak interactions: `actionLimiter allowChat off`");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowServerCommands) {
            invalidReasons.add("Action Limiter server commands should be disabled to prevent `/kill` or whispers: `actionLimiter allowServerCommands off`");
            valid = false;
        }

        return new ValidationResult(valid, invalidReasons);
    }
}
