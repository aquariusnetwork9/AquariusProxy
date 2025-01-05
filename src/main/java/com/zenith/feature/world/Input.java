package com.zenith.feature.world;

import lombok.Data;

@Data
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

    public Input(final boolean pressingForward, final boolean pressingBack, final boolean pressingLeft, final boolean pressingRight, final boolean jumping, final boolean sneaking, final boolean sprinting, final boolean leftClick, final boolean rightClick, final boolean clickMainHand) {
        this.pressingForward = pressingForward;
        this.pressingBack = pressingBack;
        this.pressingLeft = pressingLeft;
        this.pressingRight = pressingRight;
        this.jumping = jumping;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.clickMainHand = clickMainHand;
    }

    public Input() {
        this(false, false, false, false, false, false, false, false, false, true);
    }

    public Input(Input in) {
        this(in.pressingForward, in.pressingBack, in.pressingLeft, in.pressingRight, in.jumping, in.sneaking, in.sprinting, in.leftClick, in.rightClick, in.clickMainHand);
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
