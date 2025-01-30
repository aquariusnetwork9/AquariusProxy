package com.zenith.module.impl;

import com.zenith.event.module.ClientBotTick;
import com.zenith.feature.world.Input;
import com.zenith.feature.world.Input.ClickOptions.ClickTarget;
import com.zenith.feature.world.InputRequest;
import com.zenith.module.Module;
import com.zenith.util.Timer;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class Click extends Module {

    public static final int MOVEMENT_PRIORITY = 501;

    private final Timer holdRightClickTimer = Timer.createTickTimer();
    private Hand holdRightClickLastHand = Hand.MAIN_HAND;

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, this::onClientBotTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.click.enabled;
    }

    private void onClientBotTick(ClientBotTick event) {
        if (CONFIG.client.extra.click.holdLeftClick) {
            var req = InputRequest.builder().priority(MOVEMENT_PRIORITY);
            var in = Input.builder().leftClick(true);
            if (CONFIG.client.extra.click.hasRotation) {
                req.yaw(CONFIG.client.extra.click.rotationYaw)
                    .pitch(CONFIG.client.extra.click.rotationPitch);
            }
            in.sneaking(CONFIG.client.extra.click.holdSneak);
            INPUTS.submit(req.input(in.build()).build());
        } else if (CONFIG.client.extra.click.holdRightClick) {
            if (holdRightClickTimer.tick(CONFIG.client.extra.click.holdRightClickInterval)) {
                var req = InputRequest.builder().priority(MOVEMENT_PRIORITY);
                var in = Input.builder().rightClick(true);
                Hand hand = switch (CONFIG.client.extra.click.holdRightClickMode) {
                    case MAIN_HAND -> Hand.MAIN_HAND;
                    case OFF_HAND -> Hand.OFF_HAND;
                    case ALTERNATE_HANDS -> holdRightClickLastHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
                };
                holdRightClickLastHand = hand;
                in.clickOptions(new Input.ClickOptions(hand, ClickTarget.BLOCK_OR_ENTITY));
                if (CONFIG.client.extra.click.hasRotation) {
                    req.yaw(CONFIG.client.extra.click.rotationYaw)
                        .pitch(CONFIG.client.extra.click.rotationPitch);
                }
                in.sneaking(CONFIG.client.extra.click.holdSneak);
                INPUTS.submit(req.input(in.build()).build());
            } else if (CONFIG.client.extra.click.holdSneak) {
                INPUTS.submit(InputRequest.builder()
                                  .input(Input.builder()
                                             .sneaking(true)
                                             .build())
                                  .priority(0) // 0 priority allows other modules to override this if needed
                                  .build());
            }
        }
    }
}
