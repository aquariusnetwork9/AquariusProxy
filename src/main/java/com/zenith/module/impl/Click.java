package com.zenith.module.impl;

import com.zenith.event.module.ClientBotTick;
import com.zenith.feature.world.Input;
import com.zenith.feature.world.MovementInputRequest;
import com.zenith.module.Module;
import com.zenith.util.Timer;

import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class Click extends Module {

    public static final int MOVEMENT_PRIORITY = 501;

    private final Timer holdRightClickTimer = Timer.createTickTimer();
    private boolean holdRightClickLastHand = false; // true if main hand, false if off hand

    private static final Input leftClickInput = new Input();
    private static final Input rightClickInput = new Input();
    static {
        leftClickInput.leftClick = true;
        rightClickInput.rightClick = true;
    }

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
            var in = leftClickInput;
            Optional<Float> yaw;
            Optional<Float> pitch;
            if (CONFIG.client.extra.click.hasRotation) {
                yaw = Optional.of(CONFIG.client.extra.click.rotationYaw);
                pitch = Optional.of(CONFIG.client.extra.click.rotationPitch);
            } else {
                yaw = Optional.empty();
                pitch = Optional.empty();
            }
            in.sneaking = CONFIG.client.extra.click.holdSneak;
            PATHING.moveReq(new MovementInputRequest(Optional.of(in), yaw, pitch, MOVEMENT_PRIORITY));
        } else if (CONFIG.client.extra.click.holdRightClick) {
            if (holdRightClickTimer.tick(CONFIG.client.extra.click.holdRightClickInterval)) {
                var in = new Input(rightClickInput);
                switch (CONFIG.client.extra.click.holdRightClickMode) {
                    case MAIN_HAND -> in.clickMainHand = true;
                    case OFF_HAND -> in.clickMainHand = false;
                    case ALTERNATE_HANDS -> in.clickMainHand = !holdRightClickLastHand;
                }
                holdRightClickLastHand = in.clickMainHand;
                Optional<Float> yaw;
                Optional<Float> pitch;
                if (CONFIG.client.extra.click.hasRotation) {
                    yaw = Optional.of(CONFIG.client.extra.click.rotationYaw);
                    pitch = Optional.of(CONFIG.client.extra.click.rotationPitch);
                } else {
                    yaw = Optional.empty();
                    pitch = Optional.empty();
                }
                in.sneaking = CONFIG.client.extra.click.holdSneak;
                PATHING.moveReq(new MovementInputRequest(Optional.of(in), yaw, pitch, MOVEMENT_PRIORITY));
            } else if (CONFIG.client.extra.click.holdSneak) {
                var in = new Input();
                in.sneaking = true;
                // 0 priority allows other modules to override this if needed
                PATHING.move(in, 0);
            }
        }
    }
}
