package com.zenith.feature.coordobf.handlers.outbound;

import com.viaversion.nbt.mini.MNBT;
import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.CoordObfuscator;
import lombok.NonNull;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;

public class COSetEntityDataHandler implements PacketHandler<ClientboundSetEntityDataPacket, ServerSession> {
    @Override
    public ClientboundSetEntityDataPacket apply(final ClientboundSetEntityDataPacket packet, final ServerSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        List<@NonNull EntityMetadata<?, ?>> metadata = new ArrayList<>(packet.getMetadata());
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
            if (e.getEntityType() == EntityType.ITEM) {
                metadata.removeIf(m -> {
                    if (m.getType() == MetadataTypes.ITEM) {
                        return ((ItemStack) m.getValue()).getId() == ItemRegistry.ENDER_EYE.id();
                    }
                    return false;
                });
                if (metadata.isEmpty()) {
                    return null;
                }
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        List<EntityMetadata<?, ?>> modifiedMetadata = metadata.stream()
            .map(m -> {
                if (m.getType() == MetadataTypes.POSITION) {
                    return new ObjectEntityMetadata<>(m.getId(),
                                                      MetadataTypes.POSITION,
                                                      coordObf.getCoordOffset(session).offsetVector((Vector3i) m.getValue()));
                } else if (m.getType() == MetadataTypes.OPTIONAL_POSITION) {
                    return new ObjectEntityMetadata<>(m.getId(),
                                                      MetadataTypes.OPTIONAL_POSITION,
                                                      ((Optional<Vector3i>) m.getValue()).map(p -> coordObf.getCoordOffset(session)
                                                          .offsetVector(p)));
                } else if (m.getType() == MetadataTypes.NBT_TAG) {
                    return new ObjectEntityMetadata<>(m.getId(),
                                                      MetadataTypes.NBT_TAG,
                                                      coordObf.getCoordOffset(session)
                                                          .offsetNbt((MNBT) m.getValue()));
                } else if (m.getType() == MetadataTypes.OPTIONAL_GLOBAL_POS) {
                    return new ObjectEntityMetadata<>(m.getId(),
                                                      MetadataTypes.OPTIONAL_GLOBAL_POS,
                                                      ((Optional<GlobalPos>) m.getValue())
                                                          .map(globalPos ->
                                                                   new GlobalPos(
                                                                       globalPos.getDimension(),
                                                                       coordObf.getCoordOffset(session)
                                                                           .offsetX(globalPos.getX()),
                                                                       globalPos.getY(),
                                                                       coordObf.getCoordOffset(session)
                                                                           .offsetZ(globalPos.getZ())
                                                                   )
                                                          )
                    );
                }
                return m;
            }).toList();
        return new ClientboundSetEntityDataPacket(packet.getEntityId(), modifiedMetadata);
    }
}
