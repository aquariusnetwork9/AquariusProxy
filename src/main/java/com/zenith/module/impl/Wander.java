package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.module.api.Module;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class Wander extends Module {
    private final Timer pathTimer = Timers.tickTimer();
    private long lastPathTime = 0L;
    private long lastStuckWarning = 0L;
    @Getter private GoalXZ goal = new GoalXZ(0, 0);
    public static final int MOVEMENT_PRIORITY = 150;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.wander.enabled;
    }

    @Override
    public void onDisable() {
        if (BARITONE.isGoalActive(goal)) {
            debug("Stopping active pathing goal");
            BARITONE.stop();
        }
    }

    private void handleBotTickStarting(ClientBotTick.Starting starting) {
        lastPathTime = 0L;
        lastStuckWarning = 0L;
    }

    private void handleBotTick(ClientBotTick clientBotTick) {
        if (!BARITONE.isActive() && pathTimer.tick(20L)) {
            if (System.currentTimeMillis() - lastPathTime < TimeUnit.MINUTES.toMillis(1)) {
                if (System.currentTimeMillis() - lastStuckWarning > TimeUnit.MINUTES.toMillis(5)) {
                    warn("we are likely stuck :(");
                    lastStuckWarning = System.currentTimeMillis();
                }
                return;
            }

            int currentX = MathHelper.floorI(CACHE.getPlayerCache().getX());
            int currentZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());
            int radius = CONFIG.client.extra.wander.radius;
            int minRadius = CONFIG.client.extra.wander.minRadius;
            int bound = radius - minRadius;
            int goalX = ThreadLocalRandom.current().nextInt(currentX - bound, currentX + bound);
            // shift goalX to be ensure within the bounds of the active radius (area between radius and minRadius)
            goalX += goalX < currentX ? -minRadius : minRadius;
            int goalZ = ThreadLocalRandom.current().nextInt(currentZ - bound, currentZ + bound);
            goalZ += goalZ < currentZ ? -minRadius : minRadius;
            goal = new GoalXZ(goalX, goalZ);
            info("Pathing to goal: [{}, {}]", goalX, goalZ);
            BARITONE.pathTo(goal);
            lastPathTime = System.currentTimeMillis();
        }
    }
}
