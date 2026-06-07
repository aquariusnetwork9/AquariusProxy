package com.aquarius.feature.autofish;

import com.aquarius.event.module.SplashSoundEffectEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import static com.aquarius.Globals.EVENT_BUS;
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
