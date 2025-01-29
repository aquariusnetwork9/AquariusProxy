package com.zenith.feature.world;

import java.util.Optional;

public record InputRequest(Optional<Input> input, Optional<Float> yaw, Optional<Float> pitch, int priority) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Input input;
        private Float yaw;
        private Float pitch;
        private int priority = 0;

        private Builder() {}

        public Builder input(Input input) {
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
            return new InputRequest(Optional.ofNullable(input), Optional.ofNullable(yaw), Optional.ofNullable(pitch), priority);
        }
    }
}
