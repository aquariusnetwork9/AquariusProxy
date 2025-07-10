package com.zenith.feature.autofish;

import com.zenith.event.module.SplashSoundEffectEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import static com.zenith.Globals.EVENT_BUS;
import static org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound.ENTITY_FISHING_BOBBER_SPLASH;

public class AutoFishSoundHandler implements ClientEventLoopPacketHandler<ClientboundSoundPacket, ClientSession> {
    @Override
    public boolean applyAsync(ClientboundSoundPacket packet, ClientSession session) {
        if (packet.getSound() == ENTITY_FISHING_BOBBER_SPLASH) {
            EVENT_BUS.post(new SplashSoundEffectEvent(packet));
        }
        return true;
    }
}
