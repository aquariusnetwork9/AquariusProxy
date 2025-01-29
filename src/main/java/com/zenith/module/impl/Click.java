package com.zenith.module.impl;

import com.zenith.event.module.ClientBotTick;
import com.zenith.feature.world.Input;
import com.zenith.feature.world.InputRequest;
import com.zenith.module.Module;
import com.zenith.util.Timer;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class Click extends Module {

    public static final int MOVEMENT_PRIORITY = 501;

    private final Timer holdRightClickTimer = Timer.createTickTimer();
    private boolean holdRightClickLastHand = false; // true if main hand, false if off hand

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
                boolean mainhand = switch (CONFIG.client.extra.click.holdRightClickMode) {
                    case MAIN_HAND -> true;
                    case OFF_HAND -> false;
                    case ALTERNATE_HANDS -> !holdRightClickLastHand;
                };
                holdRightClickLastHand = mainhand;
                in.clickMainHand(mainhand);
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
