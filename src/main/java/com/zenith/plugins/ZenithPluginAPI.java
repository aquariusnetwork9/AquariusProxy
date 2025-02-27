package com.zenith.plugins;

import com.zenith.api.PluginAPI;
import com.zenith.api.PluginInfo;
import com.zenith.api.ZenithProxyPlugin;
import com.zenith.command.Command;
import com.zenith.module.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.zenith.Shared.*;

public class ZenithPluginAPI implements PluginAPI {
    @Override
    public <T> T registerConfig(String fileName, Class<T> configClass) {
        return PLUGIN_MANAGER.registerConfig(fileName, configClass);
    }

    @Override
    public void registerModule(final Module module) {
        MODULE.registerModule(module);
    }

    @Override
    public void registerCommand(final Command command) {
        COMMAND.registerPluginCommand(command);
    }

    @Override
    public Logger getLogger(final ZenithProxyPlugin plugin) {
        var pluginInfo = getPluginInfo(plugin);
        return LoggerFactory.getLogger("Plugin." + pluginInfo.id());
    }

    @Override
    public PluginInfo getPluginInfo(final ZenithProxyPlugin plugin) {
        return PLUGIN_MANAGER.getPluginInfo(plugin);
    }
}
