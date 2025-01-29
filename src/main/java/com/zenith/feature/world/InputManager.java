package com.zenith.feature.world;

import com.zenith.event.module.ClientBotTick;
import com.zenith.module.impl.PlayerSimulation;
import com.zenith.util.RequestFuture;
import org.jetbrains.annotations.NotNull;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.EVENT_BUS;
import static com.zenith.Shared.MODULE;

public class InputManager {
    private static final InputRequest DEFAULT_MOVEMENT_INPUT_REQUEST = InputRequest.builder()
        .priority(Integer.MIN_VALUE)
        .build();
    private static final RequestFuture DEFAULT_REQUEST_FUTURE = new RequestFuture();
    private @NotNull InputRequest currentMovementInputRequest = DEFAULT_MOVEMENT_INPUT_REQUEST;
    private @NotNull RequestFuture currentMovementInputRequestFuture = DEFAULT_REQUEST_FUTURE;

    public InputManager() {
        EVENT_BUS.subscribe(
            this,
            // should be next to last in the tick handlers
            // right before player simulation
            // but after all modules that send movement inputs
            of(ClientBotTick.class, -10000, this::handleTick)
        );
    }

    /**
     * Interface to request movement on the next tick
     */

    public synchronized RequestFuture submit(final InputRequest movementInputRequest) {
        if (movementInputRequest.priority() < currentMovementInputRequest.priority()) return RequestFuture.rejected;
        currentMovementInputRequestFuture.complete(false);
        currentMovementInputRequest = movementInputRequest;
        currentMovementInputRequestFuture = new RequestFuture();
        return currentMovementInputRequestFuture;
    }

    private synchronized void handleTick(final ClientBotTick event) {
        if (currentMovementInputRequest == DEFAULT_MOVEMENT_INPUT_REQUEST) return;
        MODULE.get(PlayerSimulation.class).doMovement(currentMovementInputRequest);
        currentMovementInputRequest = DEFAULT_MOVEMENT_INPUT_REQUEST;
        currentMovementInputRequestFuture.complete(true);
        currentMovementInputRequestFuture = DEFAULT_REQUEST_FUTURE;
    }
}
