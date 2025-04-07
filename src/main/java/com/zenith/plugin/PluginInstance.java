package com.zenith.plugin;

import com.zenith.api.plugin.PluginInfo;
import com.zenith.api.plugin.ZenithProxyPlugin;
import lombok.Data;

import java.net.URLClassLoader;
import java.nio.file.Path;

@Data
public class PluginInstance {
    private final String id;
    private final Path jarPath;
    private final PluginInfo pluginInfo;
    private final URLClassLoader classLoader;
    // null before loading
    private ZenithProxyPlugin pluginInstance;
}
