package com.zenith.feature.inventory;

import com.zenith.feature.inventory.actions.InventoryAction;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Data
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@NullMarked
public class InventoryActionRequest {
    private final @Nullable Object owner;
    private final List<InventoryAction> actions;
    private final int priority;
    private final @Nullable Integer actionDelayTicks;
    private int actionExecIndex = 0;

    public boolean isCompleted() {
        return actionExecIndex >= actions.size();
    }

    public boolean isExecuting() {
        return actionExecIndex > 0 && !isCompleted();
    }

    public @Nullable InventoryAction next() {
        var index = actionExecIndex++;
        if (index >= actions.size()) return null;
        return actions.get(index);
    }

    public @Nullable InventoryAction peek() {
        var index = actionExecIndex;
        if (index >= actions.size()) return null;
        return actions.get(index);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static InventoryActionRequest noAction(Object owner, int priority) {
        return builder().owner(owner).priority(priority).build();
    }

    public static final class Builder {
        private @Nullable Object owner = null;
        private List<InventoryAction> actions = Collections.emptyList();
        private @Nullable Integer priority = null;
        private @Nullable Integer actionDelayTicks = null;

        public Builder owner(Object owner) {
            this.owner = owner;
            return this;
        }

        public Builder actions(List<InventoryAction> actions) {
            this.actions = actions;
            return this;
        }

        public Builder actions(InventoryAction... actions) {
            return actions(List.of(actions));
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder actionDelayTicks(int actionDelayTicks) {
            this.actionDelayTicks = actionDelayTicks;
            return this;
        }

        public InventoryActionRequest build() {
            Objects.requireNonNull(actions, "actions must not be null");
            Objects.requireNonNull(priority, "priority must not be null");
            return new InventoryActionRequest(owner, actions, priority, actionDelayTicks);
        }
    }
}
