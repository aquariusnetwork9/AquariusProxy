package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.module.ClientBotTick;
import com.zenith.feature.world.Input;
import com.zenith.feature.world.InputRequest;
import com.zenith.module.Module;
import com.zenith.util.Timer;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class Wander extends Module {
    private final Timer jumpTimer = Timer.createTickTimer();
    private final Timer turnTimer = Timer.createTickTimer();
    public static final int MOVEMENT_PRIORITY = 1337;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.wander.enabled;
    }

    private void handleBotTickStarting(ClientBotTick.Starting starting) {
        jumpTimer.reset();
        turnTimer.reset();
    }

    private void handleBotTick(ClientBotTick clientBotTick) {
        Input defaultInput = CONFIG.client.extra.wander.sneak ? Input.builder()
            .pressingForward(true)
            .sneaking(true)
            .build() : Input.builder()
            .pressingForward(true)
            .build();
        if (CONFIG.client.extra.wander.turn && turnTimer.tick(20L * CONFIG.client.extra.wander.turnDelaySeconds)) {
            INPUTS.submit(InputRequest.builder()
                              .input(defaultInput)
                              .yaw((float) (Math.random() * 360))
                              .pitch(0)
                              .priority(MOVEMENT_PRIORITY)
                              .build());
        } else if (CONFIG.client.extra.wander.jump && jumpTimer.tick(20L * CONFIG.client.extra.wander.jumpDelaySeconds)) {
            INPUTS.submit(InputRequest.builder()
                              .input(Input.builder()
                                         .pressingForward(true)
                                         .jumping(true)
                                         .build())
                              .priority(MOVEMENT_PRIORITY)
                              .build());
        } else if (CONFIG.client.extra.wander.jump && CONFIG.client.extra.wander.alwaysJumpInWater && MODULE.get(PlayerSimulation.class).isTouchingWater()) {
            INPUTS.submit(InputRequest.builder()
                              .input(Input.builder()
                                         .pressingForward(true)
                                         .jumping(true)
                                         .build())
                              .priority(MOVEMENT_PRIORITY)
                              .build());
        } else {
            INPUTS.submit(InputRequest.builder()
                              .input(defaultInput)
                              .priority(MOVEMENT_PRIORITY)
                              .build());
        }
    }
}
