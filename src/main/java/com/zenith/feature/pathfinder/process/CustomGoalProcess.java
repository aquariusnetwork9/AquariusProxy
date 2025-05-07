package com.zenith.feature.pathfinder.process;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PathingCommand;
import com.zenith.feature.pathfinder.PathingCommandType;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.pathfinder.goals.Goal;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import static com.zenith.Globals.PATH_LOG;

/**
 * As set by ExampleBaritoneControl or something idk
 *
 * @author leijurv
 */
public final class CustomGoalProcess extends BaritoneProcessHelper implements IBaritoneProcess {
    @Getter
    private @Nullable Goal goal;

    private @Nullable PathingRequestFuture future;
    /**
     * The current process state.
     *
     * @see State
     */
    private State state;

    public CustomGoalProcess(Baritone baritone) {
        super(baritone);
    }

    public PathingRequestFuture setGoalAndPath(Goal goal) {
        onLostControl();
        this.goal = goal;
        this.future = new PathingRequestFuture();
        this.future.setGoal(goal);
        if (this.state == State.NONE) {
            this.state = State.GOAL_SET;
        }
        if (this.state == State.EXECUTING) {
            this.state = State.PATH_REQUESTED;
        }
        this.path();
        return this.future;
    }

    private void path() {
        this.state = State.PATH_REQUESTED;
    }

    @Override
    public boolean isActive() {
        return this.state != State.NONE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, final Goal currentGoal, final PathingCommand prevCommand) {
        switch (this.state) {
            case GOAL_SET -> {
                return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            case PATH_REQUESTED -> {
                // return FORCE_REVALIDATE_GOAL_AND_PATH just once
                PathingCommand ret = new PathingCommand(this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                this.state = State.EXECUTING;
                return ret;
            }
            case EXECUTING -> {
                if (calcFailed) {
                    onLostControl();
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                if (this.goal == null || (this.goal.isInGoal(ctx.playerFeet()) && this.goal.isInGoal(baritone.getPathingBehavior().pathStart()))) {
                    PATH_LOG.info("Pathing complete");
                    future.complete(true);
                    future.notifyListeners();
                    onLostControl(); // we're there xd
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PATH);
            }
            default -> throw new IllegalStateException();
        }
    }

    @Override
    public void onLostControl() {
        this.state = State.NONE;
        if (this.future != null && !future.isCompleted()) {
            future.complete(false);
        }
        this.future = null;
        this.goal = null;
    }

    @Override
    public String displayName0() {
        return "Custom Goal " + this.goal;
    }

    protected enum State {
        NONE,
        GOAL_SET,
        PATH_REQUESTED,
        EXECUTING
    }
}
