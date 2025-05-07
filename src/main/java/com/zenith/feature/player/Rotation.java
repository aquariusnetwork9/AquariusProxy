package com.zenith.feature.player;

public record Rotation(float yaw, float pitch) {
    /**
     * Adds the yaw/pitch of the specified rotations to this
     * rotation's yaw/pitch, and returns the result.
     *
     * @param other Another rotation
     * @return The result from adding the other rotation to this rotation
     */
    public Rotation add(Rotation other) {
        return new Rotation(
            this.yaw + other.yaw,
            this.pitch + other.pitch
        );
    }

    /**
     * Subtracts the yaw/pitch of the specified rotations from this
     * rotation's yaw/pitch, and returns the result.
     *
     * @param other Another rotation
     * @return The result from subtracting the other rotation from this rotation
     */
    public Rotation subtract(Rotation other) {
        return new Rotation(
            this.yaw - other.yaw,
            this.pitch - other.pitch
        );
    }

    /**
     * @return A copy of this rotation with the pitch clamped
     */
    public Rotation clamp() {
        return new Rotation(
            this.yaw,
            clampPitch(this.pitch)
        );
    }

    /**
     * @return A copy of this rotation with the yaw normalized
     */
    public Rotation normalize() {
        return new Rotation(
            normalizeYaw(this.yaw),
            this.pitch
        );
    }

    /**
     * @return A copy of this rotation with the pitch clamped and the yaw normalized
     */
    public Rotation normalizeAndClamp() {
        return new Rotation(
            normalizeYaw(this.yaw),
            clampPitch(this.pitch)
        );
    }

    public Rotation withPitch(float pitch) {
        return new Rotation(this.yaw, pitch);
    }

    /**
     * Is really close to
     *
     * @param other another rotation
     * @return are they really close
     */
    public boolean isReallyCloseTo(Rotation other) {
        return isCloseTo(other, 0.01f);
    }

    public boolean isCloseTo(Rotation other, float threshold) {
        return yawIsClose(other, threshold) && Math.abs(this.pitch - other.pitch) < threshold;
    }

    public boolean yawIsReallyClose(Rotation other) {
        return yawIsClose(other, 0.01f);
    }

    public boolean yawIsClose(Rotation other, float threshold) {
        float yawDiff = Math.abs(normalizeYaw(yaw) - normalizeYaw(other.yaw)); // you cant fool me
        return (yawDiff < threshold || yawDiff > 360 - threshold);
    }

    /**
     * Clamps the specified pitch value between -90 and 90.
     *
     * @param pitch The input pitch
     * @return The clamped pitch
     */
    public static float clampPitch(float pitch) {
        return Math.max(-90, Math.min(90, pitch));
    }

    /**
     * Normalizes the specified yaw value between -180 and 180.
     *
     * @param yaw The input yaw
     * @return The normalized yaw
     */
    public static float normalizeYaw(float yaw) {
        float newYaw = yaw % 360F;
        if (newYaw < -180F) {
            newYaw += 360F;
        }
        if (newYaw > 180F) {
            newYaw -= 360F;
        }
        return newYaw;
    }
}
