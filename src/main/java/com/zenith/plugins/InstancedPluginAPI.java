package com.zenith.plugins;

import com.zenith.api.PluginAPI;
import com.zenith.api.PluginInfo;
import com.zenith.api.ZenithProxyPlugin;
import com.zenith.command.Command;
import com.zenith.module.Module;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.slf4j.Logger;

import static com.zenith.Globals.*;

public record InstancedPluginAPI(
    ZenithProxyPlugin pluginInstance,
    PluginInfo pluginInfo
) implements PluginAPI {
    @Override
    public <T> T registerConfig(String fileName, Class<T> configClass) {
        getLogger().debug("Registering config: {}", fileName);
        return PLUGIN_MANAGER.registerConfig(fileName, configClass);
    }

    @Override
    public void registerModule(final Module module) {
        getLogger().debug("Registering module: {}", module);
        MODULE.registerModule(module);
    }

    @Override
    public void registerCommand(final Command command) {
        getLogger().debug("Registering command: {}", command);
        COMMAND.registerPluginCommand(command);
    }

    @Override
    public Logger getLogger() {
        return ComponentLogger.logger("Plugin." + pluginInfo.id());
    }

    @Override
    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }
}
