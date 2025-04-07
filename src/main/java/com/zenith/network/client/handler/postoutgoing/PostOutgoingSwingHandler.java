package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.event.module.ClientSwingEvent;
import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

import static com.zenith.Globals.EVENT_BUS;

public class PostOutgoingSwingHandler implements ClientEventLoopPacketHandler<ServerboundSwingPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundSwingPacket packet, final ClientSession session) {
        SpectatorSync.sendSwing();
        EVENT_BUS.postAsync(ClientSwingEvent.INSTANCE);
        return true;
    }
}
