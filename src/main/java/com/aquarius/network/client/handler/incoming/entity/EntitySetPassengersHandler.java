package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CLIENT_LOG;

public class EntitySetPassengersHandler implements ClientEventLoopPacketHandler<ClientboundSetPassengersPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundSetPassengersPacket packet, @NonNull ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity != null) {
            IntArrayList passengerIds = IntArrayList.wrap(packet.getPassengerIds());
            IntArrayList beforePassengerIds = entity.getPassengerIds();
            entity.setPassengerIds(passengerIds);
            for (int passenger : passengerIds) {
                Entity passengerEntity = CACHE.getEntityCache().get(passenger);
                if (passengerEntity != null) {
                    passengerEntity.mountVehicle(packet.getEntityId());
                } else {
                    CLIENT_LOG.debug("Received SetPassengersPacket with unknown passenger (id={})", passenger);
                }
            }
            for (int passenger : beforePassengerIds) {
                if (passengerIds.contains(passenger)) {
                    continue;
                }
                Entity passengerEntity = CACHE.getEntityCache().get(passenger);
                if (passengerEntity != null) {
                    passengerEntity.dismountVehicle();
                } else {
                    CLIENT_LOG.debug("Received SetPassengersPacket with unknown passenger (id={})", passenger);
                }
            }
            return true;
        } else {
            CLIENT_LOG.debug("Received ServerEntitySetPassengersPacket for invalid entity (id={})", packet.getEntityId());
            return true;
        }
    }
}
