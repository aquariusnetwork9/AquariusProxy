package com.zenith.cache.data.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class EntityLiving extends Entity {
    @Nullable
    protected Float health;
    protected Map<Effect, PotionEffect> potionEffectMap = new EnumMap<>(Effect.class);
    protected Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);

    @Override
    public void addPackets(final @NonNull Consumer<Packet> consumer) {
        if (!potionEffectMap.isEmpty()) {
            this.getPotionEffectMap().forEach((effect, potionEffect) -> consumer.accept(new ClientboundUpdateMobEffectPacket(
                this.entityId,
                effect,
                potionEffect.getAmplifier(),
                potionEffect.getDuration(),
                potionEffect.isAmbient(),
                potionEffect.isShowParticles(),
                potionEffect.isShowIcon(),
                potionEffect.isBlend()
            )));
        }
        if (!isSelfPlayer() && !getEquipment().isEmpty()) {
            consumer.accept(new ClientboundSetEquipmentPacket(this.entityId, getEquipment().entrySet().stream()
                .map(entry -> new Equipment(entry.getKey(), entry.getValue()))
                .toList()));
        }
        super.addPackets(consumer);
    }

    private boolean isSelfPlayer() {
        return this instanceof EntityPlayer player && player.isSelfPlayer();
    }

    public boolean isAlive() {
        if (removed) return false;
        // https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Entity
        EntityMetadata<?, ?> poseMetadata = getMetadata().get(6);
        if (poseMetadata != null && poseMetadata.getType() == MetadataTypes.POSE) {
            var pose = (Pose) poseMetadata.getValue();
            if (pose == Pose.DYING) return false;
        }
        return true;
    }

    public boolean isSleeping() {
        if (removed) return false;
        // https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Living_Entity
        EntityMetadata<?, ?> bedLocationMetadata = getMetadata().get(14);
        if (bedLocationMetadata != null && bedLocationMetadata.getType() == MetadataTypes.OPTIONAL_POSITION) {
            var bedLocation = (Optional<Vector3i>) bedLocationMetadata.getValue();
            if (bedLocation.isPresent()) return true;
        }
        return false;
    }
}
