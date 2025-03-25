package com.zenith.feature.pathfinder;

import okhttp3.internal.http2.Settings;

public enum PathingCommandType {
    /**
     * Set the goal and path.
     * <p>
     * If you use this alongside a {@code null} goal, it will continue along its current path and current goal.
     */
    SET_GOAL_AND_PATH,

    /**
     * Has no effect on the current goal or path, just requests a pause
     */
    REQUEST_PAUSE,

    /**
     * Set the goal (regardless of {@code null}), and request a cancel of the current path (when safe)
     */
    CANCEL_AND_SET_GOAL,

    /**
     * Set the goal and path.
     * <p>
     * If {@link Settings#cancelOnGoalInvalidation} is {@code true}, revalidate the
     * current goal, and cancel if it's no longer valid, or if the new goal is {@code null}.
     */
    REVALIDATE_GOAL_AND_PATH,

    /**
     * Continue on the current path, but set new goal and path to it
     *
     * Useful for potentially long pathing calc times and existing paths might not be bad to continue
     */
    SOFT_REPATH,

    /**
     * Set the goal and path.
     * <p>
     * Cancel the current path if the goals are not equal
     */
    FORCE_REVALIDATE_GOAL_AND_PATH,

    /**
     * Go and ask the next process what to do
     */
    DEFER,

    /**
     * Sets the goal and calculates a path, but pauses instead of immediately starting the path.
     */
    SET_GOAL_AND_PAUSE
}
