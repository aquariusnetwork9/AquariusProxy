package com.zenith.feature.coordobf.handlers.outbound;

import com.viaversion.nbt.mini.MNBT;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zenith.Globals.MODULE;

public class COSetEntityDataHandler implements PacketHandler<ClientboundSetEntityDataPacket, ServerSession> {
    @Override
    public ClientboundSetEntityDataPacket apply(final ClientboundSetEntityDataPacket packet, final ServerSession session) {
        var metadata = new ArrayList<>(packet.getMetadata());
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
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
