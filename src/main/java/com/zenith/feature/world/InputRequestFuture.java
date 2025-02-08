package com.zenith.feature.world;

import com.zenith.util.RequestFuture;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class InputRequestFuture extends RequestFuture {
    @Getter @Setter
    private volatile ClickResult clickResult = ClickResult.None.INSTANCE;
    private volatile List<Consumer<InputRequestFuture>> executedListeners = Collections.emptyList();

    public static final InputRequestFuture rejected = wrap(immediateFuture(false));

    /**
     * Add a callback function to be run if the input was completed, accepted, and executed.
     * Called after the client bot tick event.
     */
    public synchronized void addInputExecutedListener(Consumer<InputRequestFuture> listener) {
        if (executedListeners.isEmpty()) {
            executedListeners = new ArrayList<>(1);
        }
        executedListeners.add(listener);
    }

    public synchronized void notifyListeners() {
        if (executedListeners.isEmpty()) return;
        executedListeners.forEach(listener -> listener.accept(this));
    }

    public static InputRequestFuture wrap(RequestFuture future) {
        var inputRequestFuture = new InputRequestFuture();
        inputRequestFuture.setAccepted(future.isAccepted());
        inputRequestFuture.setCompleted(future.isCompleted());
        return inputRequestFuture;
    }

}
