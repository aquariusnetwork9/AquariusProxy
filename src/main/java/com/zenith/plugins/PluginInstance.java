package com.zenith.plugins;

import com.zenith.api.PluginInfo;
import com.zenith.api.ZenithProxyPlugin;
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
