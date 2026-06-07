package com.aquarius.feature.ratelimiter;

import com.aquarius.util.timer.Timer;
import com.aquarius.util.timer.Timers;
import lombok.Getter;
import lombok.Setter;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.SERVER_LOG;

public class PacketRateLimiter {
    private final IntervalledCounter counter = new IntervalledCounter((long) (CONFIG.server.packetRateLimiter.intervalSeconds * 1.0e9));
    private final Timer timer = Timers.timer();
    @Setter @Getter
    private boolean active = true;

    // returns true if rate limit is exceeded
    public synchronized boolean countPacket() {
        if (!active) return false;
        counter.updateAndAdd(1, System.nanoTime());
        if (CONFIG.server.packetRateLimiter.logRate && timer.tick(5000)) {
            SERVER_LOG.info("[PacketRateLimiter] {}", getRate());
        }
        return CONFIG.server.packetRateLimiter.maxPacketsPerInterval <= counter.getRate();
    }

    public double getRate() {
        return counter.getRate();
    }
}
