package com.zenith.api;

import com.zenith.command.Command;
import com.zenith.module.Module;

public interface PluginAPI {
    /**
     * Initializes and loads a configuration file for your plugin.
     * @param fileName The name of the file to save and load. Example: "example-config" would have its config at: "plugins/example-config.json"
     * @param configClass The configuration POJO class that is saved and loaded
     * @return Configuration instance. This instance must be used for access and modifications to the configuration.
     *         If no configuration file exists yet, a new instance will still be created and returned.
     */
    <T> T registerConfig(String fileName, Class<T> configClass);

    /**
     * Registers a {@link Module}.
     * Modules can listen to events, be toggled on and off, and register packet handlers
     */
    void registerModule(Module module);

    /**
     * Registers a {@link Command}.
     * Commands can be executed in the terminal, by players in-game, and in discord.
     */
    void registerCommand(Command command);
}
