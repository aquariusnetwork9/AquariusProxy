package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.player.ClickTarget;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.module.api.Module;
import com.aquarius.util.timer.Timer;
import com.aquarius.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;

import java.util.List;
import java.util.Objects;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INPUTS;

public class Click extends Module {
    private final Timer holdRightClickTimer = Timers.tickTimer();
    private final Timer holdLeftClickTimer = Timers.tickTimer();
    private Hand holdRightClickLastHand = Hand.MAIN_HAND;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onClientBotTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.click.enabled;
    }

    public int getPriority() {
        return Objects.requireNonNullElse(CONFIG.client.extra.click.priority, 9000);
    }

    private void onClientBotTick(ClientBotTick event) {
        if (CONFIG.client.extra.click.holdLeftClick) {
            if (holdLeftClickTimer.tick(CONFIG.client.extra.click.holdLeftClickInterval)) {
                var req = InputRequest.builder().priority(getPriority());
                var in = Input.builder().leftClick(true);
                if (CONFIG.client.extra.click.hasRotation) {
                    req.yaw(CONFIG.client.extra.click.rotationYaw)
                        .pitch(CONFIG.client.extra.click.rotationPitch);
                }
                in.sneaking(CONFIG.client.extra.click.holdSneak);
                var clickTarget = switch (CONFIG.client.extra.click.holdClickTarget) {
                    case ANY -> ClickTarget.Any.INSTANCE;
                    case NONE -> ClickTarget.None.INSTANCE;
                    case ENTITY -> ClickTarget.AnyEntity.INSTANCE;
                    case BLOCK -> ClickTarget.AnyBlock.INSTANCE;
                };
                in.clickTarget(clickTarget);
                INPUTS.submit(req
                    .owner(this)
                    .input(in.build())
                    .build());
            } else if (CONFIG.client.extra.click.holdSneak) {
                INPUTS.submit(InputRequest.builder()
                    .owner(this)
                    .input(Input.builder()
                        .sneaking(true)
                        .build())
                    .priority(0) // 0 priority allows other modules to override this if needed
                    .build());
            }
        } else if (CONFIG.client.extra.click.holdRightClick) {
            if (holdRightClickTimer.tick(CONFIG.client.extra.click.holdRightClickInterval)) {
                var req = InputRequest.builder().owner(this).priority(getPriority());
                var in = Input.builder().rightClick(true);
                Hand hand = switch (CONFIG.client.extra.click.holdRightClickMode) {
                    case MAIN_HAND -> Hand.MAIN_HAND;
                    case OFF_HAND -> Hand.OFF_HAND;
                    case ALTERNATE_HANDS -> holdRightClickLastHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
                };
                holdRightClickLastHand = hand;
                in.hand(hand);
                if (CONFIG.client.extra.click.hasRotation) {
                    req.yaw(CONFIG.client.extra.click.rotationYaw)
                        .pitch(CONFIG.client.extra.click.rotationPitch);
                }
                in.sneaking(CONFIG.client.extra.click.holdSneak);
                var clickTarget = switch (CONFIG.client.extra.click.holdClickTarget) {
                    case ANY -> ClickTarget.Any.INSTANCE;
                    case NONE -> ClickTarget.None.INSTANCE;
                    case ENTITY -> ClickTarget.AnyEntity.INSTANCE;
                    case BLOCK -> ClickTarget.AnyBlock.INSTANCE;
                };
                in.clickTarget(clickTarget);
                INPUTS.submit(req
                    .input(in.build())
                    .build());
            } else if (CONFIG.client.extra.click.holdSneak) {
                INPUTS.submit(InputRequest.builder()
                    .owner(this)
                    .input(Input.builder()
                        .sneaking(true)
                        .build())
                    .priority(0) // 0 priority allows other modules to override this if needed
                    .build());
            }
        }
    }
}
