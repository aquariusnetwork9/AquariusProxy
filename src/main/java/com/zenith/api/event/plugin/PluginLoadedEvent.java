package com.zenith.api.event.plugin;

import com.zenith.api.plugin.PluginInfo;

public record PluginLoadedEvent(PluginInfo pluginInfo) { }
