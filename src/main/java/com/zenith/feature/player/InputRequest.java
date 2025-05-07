package com.zenith.feature.player;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Data
@RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Accessors(fluent = true)
@NullMarked
public class InputRequest {
    private final @Nullable Object owner;
    private final @Nullable Input input;
    private final @Nullable Float yaw;
    private final @Nullable Float pitch;
    private final int priority;

    public static Builder builder() {
        return new Builder();
    }

    public static InputRequest noInput(Object owner, int priority) {
        return builder().owner(owner).priority(priority).build();
    }

    public static final class Builder {
        private @Nullable Object owner = null;
        private @Nullable Input input;
        private @Nullable Float yaw;
        private @Nullable Float pitch;
        private int priority = 0;

        private Builder() {}

        public Builder owner(Object owner) {
            this.owner = owner;
            return this;
        }

        public Builder input(@Nullable Input input) {
            this.input = input;
            return this;
        }

        public Builder yaw(float yaw) {
            this.yaw = yaw;
            return this;
        }

        public Builder pitch(float pitch) {
            this.pitch = pitch;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public InputRequest build() {
//            Objects.requireNonNull(owner, "owner must not be null");
            return new InputRequest(owner, input, yaw, pitch, priority);
        }
    }
}
