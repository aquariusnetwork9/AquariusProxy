package com.zenith.feature.pathfinder;

import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.util.RequestFuture;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class PathingRequestFuture extends RequestFuture {
    @Getter @Setter
    private volatile @Nullable Goal goal = null;
    private volatile List<Consumer<PathingRequestFuture>> executedListeners = Collections.emptyList();

    public static final PathingRequestFuture rejected = wrap(immediateFuture(false));

    public synchronized void addExecutedListener(Consumer<PathingRequestFuture> executedListener) {
        if (executedListeners.isEmpty()) {
            executedListeners = new ArrayList<>(1);
        }
        executedListeners.add(executedListener);
    }

    public synchronized void notifyListeners() {
        if (executedListeners.isEmpty()) return;
        executedListeners.forEach(listener -> listener.accept(this));
    }

    public static PathingRequestFuture wrap(RequestFuture future) {
        var pathingRequestFuture = new PathingRequestFuture();
        pathingRequestFuture.setAccepted(future.isAccepted());
        pathingRequestFuture.setCompleted(future.isCompleted());
        return pathingRequestFuture;
    }
}
