package com.zenith.feature.world;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;

@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor
public class Input {
    public boolean pressingForward;
    public boolean pressingBack;
    public boolean pressingLeft;
    public boolean pressingRight;
    public boolean jumping;
    public boolean sneaking;
    public boolean sprinting;
    public boolean leftClick;
    public boolean rightClick;
    public ClickOptions clickOptions = ClickOptions.DEFAULT;

    public record ClickOptions(Hand hand, ClickTarget target) {
        public enum ClickTarget {
            BLOCK_OR_ENTITY, BLOCK, ENTITY;
        }
        public static final ClickOptions DEFAULT = new ClickOptions(Hand.MAIN_HAND, ClickTarget.BLOCK_OR_ENTITY);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Input(Input in) {
        this(in.pressingForward, in.pressingBack, in.pressingLeft, in.pressingRight, in.jumping, in.sneaking, in.sprinting, in.leftClick, in.rightClick, in.clickOptions);
    }

    public void apply(Input in) {
        if (!pressingForward || !pressingBack) {
            this.pressingForward = in.pressingForward;
            this.pressingBack = in.pressingBack;
        }
        if (!in.pressingLeft || !in.pressingRight) {
            this.pressingLeft = in.pressingLeft;
            this.pressingRight = in.pressingRight;
        }
        this.jumping = in.jumping;
        this.sneaking = in.sneaking;
        this.sprinting = in.sprinting;
        if (this.sprinting && (this.pressingBack || this.sneaking)) {
            this.sprinting = false;
        }
        this.leftClick = in.leftClick;
        this.rightClick = in.rightClick;
        this.clickOptions = in.clickOptions;
    }

    public void reset() {
        pressingForward = false;
        pressingBack = false;
        pressingLeft = false;
        pressingRight = false;
        jumping = false;
        sneaking = false;
        sprinting = false;
        leftClick = false;
        rightClick = false;
        clickOptions = ClickOptions.DEFAULT;
    }

    public String log() {
        String out = "Input:";
        // only log true values
        if (pressingForward) {
            out += " forward";
        }
        if (pressingBack) {
            out += " back";
        }
        if (pressingLeft) {
            out += " left";
        }
        if (pressingRight) {
            out += " right";
        }
        if (jumping) {
            out += " jump";
        }
        if (sneaking) {
            out += " sneak";
        }
        if (sprinting) {
            out += " sprint";
        }


        // but if none are true, log that
        if (out.equals("Input:")) {
            out += " none";
        }
        return out;
    }

    public static final class Builder {
        private boolean pressingForward;
        private boolean pressingBack;
        private boolean pressingLeft;
        private boolean pressingRight;
        private boolean jumping;
        private boolean sneaking;
        private boolean sprinting;
        private boolean leftClick;
        private boolean rightClick;
        private ClickOptions clickOptions = ClickOptions.DEFAULT;

        private Builder() {}

        public Builder pressingForward(boolean pressingForward) {
            this.pressingForward = pressingForward;
            return this;
        }

        public Builder pressingBack(boolean pressingBack) {
            this.pressingBack = pressingBack;
            return this;
        }

        public Builder pressingLeft(boolean pressingLeft) {
            this.pressingLeft = pressingLeft;
            return this;
        }

        public Builder pressingRight(boolean pressingRight) {
            this.pressingRight = pressingRight;
            return this;
        }

        public Builder jumping(boolean jumping) {
            this.jumping = jumping;
            return this;
        }

        public Builder sneaking(boolean sneaking) {
            this.sneaking = sneaking;
            return this;
        }

        public Builder sprinting(boolean sprinting) {
            this.sprinting = sprinting;
            return this;
        }

        public Builder leftClick(boolean leftClick) {
            this.leftClick = leftClick;
            return this;
        }

        public Builder rightClick(boolean rightClick) {
            this.rightClick = rightClick;
            return this;
        }

        public Builder clickOptions(ClickOptions clickOptions) {
            this.clickOptions = clickOptions;
            return this;
        }

        public Input build() {
            Input input = new Input();
            input.setPressingForward(pressingForward);
            input.setPressingBack(pressingBack);
            input.setPressingLeft(pressingLeft);
            input.setPressingRight(pressingRight);
            input.setJumping(jumping);
            input.setSneaking(sneaking);
            input.setSprinting(sprinting);
            input.setLeftClick(leftClick);
            input.setRightClick(rightClick);
            input.setClickOptions(clickOptions);
            return input;
        }
    }
}
