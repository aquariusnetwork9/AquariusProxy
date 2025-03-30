package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.event.proxy.PlayerLoginEvent;
import com.zenith.event.proxy.ServerConnectionRemovedEvent;
import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.feature.coordobf.handlers.inbound.*;
import com.zenith.feature.coordobf.handlers.outbound.*;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.Module;
import com.zenith.network.registry.PacketHandlerCodec;
import com.zenith.network.registry.PacketHandlerStateCodec;
import com.zenith.network.server.ServerSession;
import com.zenith.util.Config.Client.Extra.CoordObfuscation.ObfuscationMode;
import com.zenith.util.math.MathHelper;
import com.zenith.util.math.MutableVec3d;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class CoordObfuscator extends Module {
    private final Random random = new SecureRandom();
    // todo: don't think these maps are needed actually
    // non-offset pos. the cache's player pos is updated before our handlers are called (and its called async)
    private final Map<ServerSession, MutableVec3d> lastPlayerPosMap = new ConcurrentHashMap<>();
    private final Map<ServerSession, DimensionData> lastPlayerDimensionMap = new ConcurrentHashMap<>();
    private final ReferenceSet<ServerSession> activeSessions = new ReferenceOpenHashSet<>();
    @Getter
    private final MutableVec3d serverTeleportPos = new MutableVec3d(0.0, 0.0, 0.0);
    @Getter @Setter
    private boolean nextPlayerMovePacketIsTeleport = false;

    // todo: test case where player is world/server switched back to queue

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(ServerConnectionRemovedEvent.class, this::onServerConnectionRemoved),
            of(PlayerLoginEvent.class, this::onPlayerLoginEvent));
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
            .setActivePredicate(this::shouldObfuscate)
            .state(ProtocolState.CONFIGURATION, PacketHandlerStateCodec.serverBuilder()
                .registerOutbound(ClientboundRegistryDataPacket.class, new CORegistryDataPacketHandler())
                .build())
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


    @Override
    public void onEnable() {
        lastPlayerDimensionMap.clear();
        lastPlayerPosMap.clear();
        kickAllActiveConnections();
    }

    @Override
    public void onDisable() {
        lastPlayerDimensionMap.clear();
        lastPlayerPosMap.clear();
        kickAllActiveConnections();
    }

    public void onPlayerLoginEvent(final PlayerLoginEvent event) {
        try {
            if (CACHE.getPlayerCache().isRespawning()) {
                event.serverConnection().disconnect("Respawning...");
                return;
            }
            ServerSession serverConnection = event.serverConnection();
            var profile = serverConnection.getProfileCache().getProfile();
            var proxyProfile = CACHE.getProfileCache().getProfile();
            if (profile != null && proxyProfile != null && profile.getId().equals(proxyProfile.getId())) {
                info("Disabled for session: {}", profile.getName());
                return;
            }
            activeSessions.add(serverConnection);
            var playerPos = getPlayerPos(event.serverConnection());
            var coordOffset = generateOffset(event.serverConnection(), playerPos.getX(), playerPos.getZ());
            event.serverConnection().setCoordOffset(coordOffset);
            info("Offset for {}: {}, {}", profile.getName(), coordOffset.x(), coordOffset.z());
        } catch (final RuntimeException e) {
            error("Failed to generate coord offset", e);
            event.serverConnection().disconnect("bye");
        }
    }

    public void onServerConnectionRemoved(final ServerConnectionRemovedEvent event) {
        lastPlayerPosMap.remove(event.serverConnection());
        lastPlayerDimensionMap.remove(event.serverConnection());
        activeSessions.remove(event.serverConnection());
    }

    public void onConfigChange() {
        // todo: respawn the player with new offset
        kickAllActiveConnections();
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
            session.disconnect("Too close to spawn");
            return new CoordOffset(random.nextInt(12345, 999999), random.nextInt(12345, 999999));
        }

        if (CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate) {
            DimensionData dimension = lastPlayerDimensionMap.get(session);
            if (dimension != null && dimension == DimensionRegistry.THE_NETHER) {
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
        MutableVec3d mutableVec3d = this.lastPlayerPosMap.get(session);
        if (mutableVec3d == null) {
            mutableVec3d = new MutableVec3d(x, 0.0, z);
            this.lastPlayerPosMap.put(session, mutableVec3d);
            return;
        }
        mutableVec3d.setX(x);
        mutableVec3d.setZ(z);
    }

    public MutableVec3d getPlayerPos(final ServerSession session) {
        MutableVec3d mutableVec3d = this.lastPlayerPosMap.get(session);
        if (mutableVec3d == null) {
            mutableVec3d = new MutableVec3d(CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getY(), CACHE.getPlayerCache().getZ());
            this.lastPlayerPosMap.put(session, mutableVec3d);
        }
        return mutableVec3d;
    }

    public void setPlayerAcceptTeleport(final ServerSession session, final int id) {
        if (session.isSpectator()) return; // ignore for spectators
        // next player move packet needs to align with server's coords exactly
        this.setNextPlayerMovePacketIsTeleport(true);
    }

    public void setServerTeleportPos(final ServerSession session, final double x, final double y, final double z, final int teleportId) {
        if (session.isSpectator()) return; // ignore for spectators
        this.serverTeleportPos.setX(x);
        this.serverTeleportPos.setY(y);
        this.serverTeleportPos.setZ(z);
    }

    public void onServerTeleport(final ServerSession session, final double x, final double y, final double z, final int teleportId) {
        setServerTeleportPos(session, x, y, z, teleportId);
        if (session.isRespawning()) {
            var futureOffset = generateOffset(session, x, z);
            info("Regenerated offset due to respawn teleport: {} {}", futureOffset.x(), futureOffset.z());
            session.setCoordOffset(futureOffset);
            session.setRespawning(false);
        }
    }

    public void onRespawn(final ServerSession session, final int dimension) {
        setRespawnDimension(session, dimension);
        session.setRespawning(true);
    }

    public void setRespawnDimension(final ServerSession session, final int dimension) {
        if (session.isSpectator()) return; // ignore for spectators
        info("Setting respawn dimension: {}", dimension);
        DimensionData newDim = CACHE.getChunkCache().getDimensionRegistry().get(dimension);
        this.lastPlayerDimensionMap.put(session, newDim);
    }

    private void kickAllActiveConnections() {
        var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            var con = connections[i];
            con.disconnect("bye");
        }
    }

    public boolean shouldObfuscate(ServerSession session) {
        return activeSessions.contains(session);
    }

    public record ValidationResult(boolean valid, List<String> invalidReasons) {}

    public ValidationResult validateSetup() {
        // check all modules and settings that could lead to leaking coordinates or the offset

        boolean valid = true;
        List<String> invalidReasons = new ArrayList<>();

        // ingame commands, both sending/receiving
        if (CONFIG.inGameCommands.enable) {
            invalidReasons.add("In-game commands should be disabled, many commands leak coordinates in outputs and behavior");
            valid = false;
        }
        if (CONFIG.client.extra.chatControl.enabled) {
            invalidReasons.add("Chat Control should be disabled, many commands leak coordinates in outputs and behavior");
            valid = false;
        }
        if (!CONFIG.client.extra.actionLimiter.enabled) {
            invalidReasons.add("Action Limiter should be enabled to prevent long distance movement and respawning");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowMovement) {
            invalidReasons.add("Action Limiter `allowMovement` should be disabled to prevent long distance movement");
            valid = false;
        }
        if (CONFIG.client.extra.actionLimiter.allowRespawn) {
            invalidReasons.add("Action Limiter `allowRespawn` should be disabled to prevent respawning");
            valid = false;
        }

        return new ValidationResult(valid, invalidReasons);
    }
}
