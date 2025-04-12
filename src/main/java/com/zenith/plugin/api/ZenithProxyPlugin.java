package com.zenith.plugin.api;

public interface ZenithProxyPlugin {
    /**
     * Called immediately when the plugin class is loaded.
     *
     * Initialize configurations, modules, and commands here.
     */
    void onLoad(PluginAPI pluginAPI);
}
