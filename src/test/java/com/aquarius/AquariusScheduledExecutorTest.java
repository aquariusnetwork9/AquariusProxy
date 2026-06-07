package com.aquarius;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.aquarius.util.Wait;
import com.aquarius.util.AquariusScheduledExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AquariusScheduledExecutorTest {

    @Test
    public void testDefaultExceptionHandler() {
        AtomicBoolean defaultHandlerCalled = new AtomicBoolean(false);
        try (var executor = new AquariusScheduledExecutor(4, new ThreadFactoryBuilder()
            .setNameFormat("AquariusProxy Scheduled Executor - #%d")
            .setUncaughtExceptionHandler((thread, e) -> {
                defaultHandlerCalled.set(true);
            })
            .build())) {
            executor.execute(() -> {
                throw new RuntimeException("asdf");
            });
            assertTrue(Wait.waitUntil(defaultHandlerCalled::get, 3));
        }
    }
}
