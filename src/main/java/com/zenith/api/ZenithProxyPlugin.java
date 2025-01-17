package com.zenith.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ZenithProxyPlugin {
    /**
     * A unique, single word, identifier for this plugin
     */
    String id();

    /**
     * Called immediately when the plugin class is loaded.
     *
     * Initialize configurations, modules, and commands here.
     */
    void onLoad(PluginAPI pluginAPI);

    /**
     * Utility method to get an identifiable logger for this plugin
     */
    default Logger getLogger() {
        return LoggerFactory.getLogger("Plugin." + id());
    }
}
