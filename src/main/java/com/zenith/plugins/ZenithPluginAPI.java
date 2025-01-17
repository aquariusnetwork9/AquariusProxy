package com.zenith.plugins;

import com.zenith.api.PluginAPI;
import com.zenith.command.Command;
import com.zenith.module.Module;

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
}
