package com.aquarius.plugin;

import com.aquarius.command.api.Command;
import com.aquarius.module.api.Module;
import com.aquarius.plugin.api.ConfigSerializer;
import com.aquarius.plugin.api.PluginAPI;
import com.aquarius.plugin.api.PluginInfo;
import com.aquarius.plugin.api.AquariusProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import static com.aquarius.Globals.*;
import static com.aquarius.util.KotlinUtil.isKotlinObject;

public record InstancedPluginAPI(
    AquariusProxyPlugin pluginInstance,
    PluginInfo pluginInfo
) implements PluginAPI {
    @Override
    public <T> T registerConfig(String fileName, Class<T> configClass) {
        return registerConfig(fileName, configClass, isKotlinObject(configClass)
            ? new KotlinObjectGsonConfigSerializer(configClass)
            : DefaultGsonConfigSerializer.INSTANCE
        );
    }

    @Override
    public <T> T registerConfig(String fileName, Class<T> configClass, ConfigSerializer serializer) {
        getLogger().debug("Registering config: {} [{}] using serializer: {}", fileName, configClass.getSimpleName(), serializer.getClass().getSimpleName());
        return PLUGIN_MANAGER.registerConfig(fileName, configClass, serializer);
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
    public ComponentLogger getLogger() {
        return ComponentLogger.logger("Plugin." + pluginInfo.id());
    }

    @Override
    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }
}
