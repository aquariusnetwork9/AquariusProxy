package com.zenith.feature.world;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
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
    public boolean clickMainHand;

    public Input(Input in) {
        this(in.pressingForward, in.pressingBack, in.pressingLeft, in.pressingRight, in.jumping, in.sneaking, in.sprinting, in.leftClick, in.rightClick, in.clickMainHand);
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
        this.clickMainHand = in.clickMainHand;
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
        clickMainHand = true;
    }
}
