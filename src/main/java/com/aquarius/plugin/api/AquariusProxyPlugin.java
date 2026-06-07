package com.aquarius.plugin.api;

public interface AquariusProxyPlugin {
    /**
     * Called immediately when the plugin class is loaded.
     *
     * Initialize configurations, modules, and commands here.
     */
    void onLoad(PluginAPI pluginAPI);
}
