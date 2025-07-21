package com.zenith.feature.pathfinder;

import com.zenith.feature.pathfinder.behavior.PathingBehavior;
import com.zenith.feature.pathfinder.executor.PathExecutor;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.process.IBaritoneProcess;
import com.zenith.mc.block.BlockPos;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.CLIENT_LOG;

public class PathingControlManager {

    private final Baritone baritone;
    private final HashSet<IBaritoneProcess> processes; // unGh
    private final List<IBaritoneProcess> active;
    private IBaritoneProcess inControlLastTick;
    private IBaritoneProcess inControlThisTick;
    private PathingCommand command;

    public PathingControlManager(Baritone baritone) {
        this.baritone = baritone;
        this.processes = new HashSet<>();
        this.active = new ArrayList<>();
    }

//    @Override
    public void registerProcess(IBaritoneProcess process) {
        process.stop(); // make sure it's reset
        processes.add(process);
    }

    public void cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
        inControlLastTick = null;
        inControlThisTick = null;
        command = null;
        active.clear();
        for (IBaritoneProcess proc : processes) {
            proc.stop();
            if (proc.isActive() && !proc.isTemporary()) { // it's okay only for a temporary thing (like combat pause) to maintain control even if you say to cancel
                throw new IllegalStateException(proc.displayName());
            }
        }
    }

    public boolean isActive() {
        return !active.isEmpty();
    }

//    @Override
    public Optional<IBaritoneProcess> mostRecentInControl() {
        return Optional.ofNullable(inControlThisTick);
    }

//    @Override
    public Optional<PathingCommand> mostRecentCommand() {
        return Optional.ofNullable(command);
    }

    public void preTick() {
        inControlLastTick = inControlThisTick;
        inControlThisTick = null;
        PathingBehavior p = baritone.getPathingBehavior();
        command = executeProcesses(p.getGoal(), command);
        if (command == null) {
            p.cancelSegmentIfSafe();
            p.secretInternalSetGoal(null);
            return;
        }
        if (!Objects.equals(inControlThisTick, inControlLastTick) && command.commandType != PathingCommandType.REQUEST_PAUSE && inControlLastTick != null && !inControlLastTick.isTemporary()) {
            // if control has changed from a real process to another real process, and the new process wants to do something
            p.cancelSegmentIfSafe();
            // get rid of the in progress stuff from the last process
        }
        switch (command.commandType) {
            case SET_GOAL_AND_PAUSE -> p.secretInternalSetGoalAndPath(command);
            case REQUEST_PAUSE -> p.requestPause();
            case CANCEL_AND_SET_GOAL -> {
                p.secretInternalSetGoal(command.goal);
                p.cancelSegmentIfSafe();
            }
            case FORCE_REVALIDATE_GOAL_AND_PATH, REVALIDATE_GOAL_AND_PATH, SOFT_REPATH -> {
                if (!p.isPathing() && !p.getInProgress().isPresent()) {
                    p.secretInternalSetGoalAndPath(command);
                }
            }
            case SET_GOAL_AND_PATH -> {
                // now this i can do
                if (command.goal != null) {
                    p.secretInternalSetGoalAndPath(command);
                }
            }
        }
    }

    public void postTick() {
        // if we did this in pretick, it would suck
        // we use the time between ticks as calculation time
        // therefore, we only cancel and recalculate after the tick for the current path has executed
        // "it would suck" means it would actually execute a path every other tick
        if (command == null) {
            return;
        }
        PathingBehavior p = baritone.getPathingBehavior();
        switch (command.commandType) {
            case FORCE_REVALIDATE_GOAL_AND_PATH -> {
                if (command.goal == null || forceRevalidate(command.goal) || revalidateGoal(command.goal)) {
                    // pwnage
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
            }
            case SOFT_REPATH -> {
                p.secretInternalSetGoalAndPath(command);
            }
            case REVALIDATE_GOAL_AND_PATH -> {
                if ((command.goal == null || revalidateGoal(command.goal))) {
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
            }
        }
    }

    public boolean forceRevalidate(Goal newGoal) {
        PathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            if (newGoal.isInGoal(current.getPath().getDest())) {
                return false;
            }
            return !newGoal.equals(current.getPath().getGoal());
        }
        return false;
    }

    public boolean revalidateGoal(Goal newGoal) {
        PathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            Goal intended = current.getPath().getGoal();
            BlockPos end = current.getPath().getDest();
            if (intended.isInGoal(end) && !newGoal.isInGoal(end)) {
                // this path used to end in the goal
                // but the goal has changed, so there's no reason to continue...
                return true;
            }
        }
        return false;
    }


    public PathingCommand executeProcesses(final Goal currentGoal, final PathingCommand prevCommand) {
        for (IBaritoneProcess process : processes) {
            if (process.isActive()) {
                if (!active.contains(process)) {
                    // put a newly active process at the very front of the queue
                    active.addFirst(process);
                }
            } else {
                active.remove(process);
            }
        }
        // ties are broken by which was added to the beginning of the list first
        active.sort(Comparator.comparingDouble(IBaritoneProcess::priority).reversed());

        Iterator<IBaritoneProcess> iterator = active.iterator();
        while (iterator.hasNext()) {
            IBaritoneProcess proc = iterator.next();
            long before = System.nanoTime();
            boolean procCalcFailedLastTick = Objects.equals(proc, inControlLastTick) && baritone.getPathingBehavior().calcFailedLastTick();
            PathingCommand exec = proc.onTick(procCalcFailedLastTick, baritone.getPathingBehavior().isSafeToCancel(), currentGoal, prevCommand);
            long after = System.nanoTime();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(after - before);
            if (elapsedMs > 50) {
                CLIENT_LOG.warn("Proc: {} tick took {}ms", proc.displayName(), elapsedMs);
            }
            if (exec == null) {
                if (proc.isActive()) {
                    throw new IllegalStateException(proc.displayName() + " actively returned null PathingCommand");
                }
                // no need to call onLostControl; they are reporting inactive.
            } else if (exec.commandType != PathingCommandType.DEFER) {
                inControlThisTick = proc;
                if (!proc.isTemporary()) {
                    iterator.forEachRemaining(IBaritoneProcess::onLostControl);
                }
                return exec;
            }
        }
        return null;
    }
}
