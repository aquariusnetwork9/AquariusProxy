package com.zenith.feature.pathfinder.process;

import static com.zenith.Globals.PATH_LOG;

public interface ProcessUtil {
    default String processLogPrefix() { return "[" + this.getClass().getSimpleName() + "] "; }

    default void debug(String msg) {
        PATH_LOG.debug(processLogPrefix() + msg);
    }

    default void debug(String msg, Object... args) {
        PATH_LOG.debug(processLogPrefix() + msg, args);
    }

    default void info(String msg) {
        PATH_LOG.info(processLogPrefix() + msg);
    }

    default void info(String msg, Object... args) {
        PATH_LOG.info(processLogPrefix() + msg, args);
    }

    default void warn(String msg) {
        PATH_LOG.warn(processLogPrefix() + msg);
    }

    default void warn(String msg, Object... args) {
        PATH_LOG.warn(processLogPrefix() + msg, args);
    }

    default void error(String msg) {
        PATH_LOG.error(processLogPrefix() + msg);
    }

    default void error(String msg, Object... args) {
        PATH_LOG.error(processLogPrefix() + msg, args);
    }
}
