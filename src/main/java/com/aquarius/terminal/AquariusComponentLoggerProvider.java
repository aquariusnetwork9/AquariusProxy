package com.aquarius.terminal;

import ch.qos.logback.classic.Level;
import com.google.auto.service.AutoService;
import com.aquarius.terminal.logback.LogSourceFilter;
import com.aquarius.util.ComponentSerializer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.logger.slf4j.ComponentLoggerProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@AutoService(ComponentLoggerProvider.class)
public class AquariusComponentLoggerProvider implements ComponentLoggerProvider {
    private final Map<String, ComponentLogger> loggers = new ConcurrentHashMap<>();

    @Override
    public @NotNull ComponentLogger logger(@NotNull final LoggerHelper helper, @NotNull final String name) {
        final ComponentLogger initial = this.loggers.get(name);
        if (initial != null) return initial;
        final Logger backing = LoggerFactory.getLogger(name);
        LogSourceFilter.registerLogger(name, Level.TRACE);
        return helper.delegating(backing, ComponentSerializer::serializeAnsi);
    }
}
