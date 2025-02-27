package com.zenith.plugins;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zenith.api.PluginInfo;
import com.zenith.api.ZenithProxyPlugin;
import com.zenith.util.ImageInfo;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zenith.Shared.OBJECT_MAPPER;
import static com.zenith.Shared.PLUGIN_LOG;

public class PluginManager {
    static final Path pluginsPath = Path.of("plugins");
    private final BiMap<String, ZenithProxyPlugin> idToInstance = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<String, PluginInfo> idToInfo = new ConcurrentHashMap<>();
    @Getter final Map<String, ConfigInstance> pluginConfigurations = new ConcurrentHashMap<>();
    private final ZenithPluginAPI api = new ZenithPluginAPI();
    private final AtomicBoolean initialized = new AtomicBoolean(false);


    public String getId(final ZenithProxyPlugin pluginInstance) {
        return idToInstance.inverse().get(pluginInstance);
    }

    public PluginInfo getPluginInfo(final ZenithProxyPlugin pluginInstance) {
        return idToInfo.get(getId(pluginInstance));
    }

    public record ConfigInstance(String fileName, Object instance, Class<?> clazz) { }

    public synchronized void initialize() {
        if (ImageInfo.inImageCode()) {
            // todo: warn about linux channel incompatibility if there are plugin jars present
            return;
        }
        if (initialized.compareAndSet(false, true)) {
            loadPlugins();
        }
    }

    private void loadPlugins() {
        if (!pluginsPath.toFile().exists()) return;
        try (var jarStream = Files.newDirectoryStream(pluginsPath, p -> p.toFile().isFile() && p.toString().endsWith(".jar"))) {
            for (var jarPath : jarStream) {
                loadPotentialPluginJar(jarPath);
            }
        } catch (Throwable e) {
            PLUGIN_LOG.error("Error loading plugins", e);
        }
    }

    private void loadPotentialPluginJar(final Path jarPath) {
        try (var classloader = new URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()}, getClass().getClassLoader())) {
            PluginInfo pluginJson = extractPluginJson(classloader, jarPath);
            var id = Objects.requireNonNull(pluginJson.id(), "Plugin id is null");
            if (idToInfo.containsKey(id)) {
                PLUGIN_LOG.error("Plugin id already exists: {} from: {} (json)", id, jarPath);
                return;
            }
            String entrypoint = Objects.requireNonNull(pluginJson.entrypoint(), "Plugin entrypoint is null");
            Class<?> pluginClass = classloader.loadClass(entrypoint);
            if (!ZenithProxyPlugin.class.isAssignableFrom(pluginClass)) {
                PLUGIN_LOG.error("Plugin does not implement ZenithProxyPlugin interface: {}", jarPath);
                return;
            }
            ZenithProxyPlugin plugin = (ZenithProxyPlugin) pluginClass.getDeclaredConstructor().newInstance();

            if (idToInstance.get(id) != null) {
                PLUGIN_LOG.error("Plugin id already exists: {} from: {} (instance)", id, jarPath);
                return;
            }
            idToInstance.put(id, plugin);
            idToInfo.put(id, pluginJson);
            try {
                plugin.onLoad(api);
            } catch (final Throwable e) {
                PLUGIN_LOG.error("Exception in plugin onLoad: {}", jarPath, e);
                idToInstance.remove(id);
                idToInfo.remove(id);
            }
        } catch (Throwable e) {
            PLUGIN_LOG.error("Error loading plugin: {}", jarPath, e);
        }
    }

    @SneakyThrows
    private PluginInfo extractPluginJson(URLClassLoader classLoader, Path path) {
        try (var stream = classLoader.getResourceAsStream("plugin.json")) {
            return OBJECT_MAPPER.readValue(stream, PluginInfo.class);
        } catch (IOException e) {
            PLUGIN_LOG.error("Error reading plugin.json: {}", path, e);
            throw e;
        }
    }

    public synchronized <T> T registerConfig(String fileName, Class<T> clazz) {
        if (pluginConfigurations.containsKey(fileName)) {
            throw new RuntimeException("Config already registered: " + fileName);
        }
        var config = loadPluginConfig(fileName, clazz);
        ConfigInstance configInstance = new ConfigInstance(fileName, config, clazz);
        pluginConfigurations.put(fileName, configInstance);
        return config;
    }

    @SneakyThrows
    private <T> T loadPluginConfig(String fileName, Class<T> clazz) {
        try {
            PLUGIN_LOG.info("Loading plugin config...");
            File configFile = new File("plugins/" + fileName + ".json");
            T config;
            if (configFile.exists()) {
                try (Reader reader = new FileReader(configFile)) {
                    final Gson GSON = new GsonBuilder()
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();
                    config = GSON.fromJson(reader, clazz);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to load plugin config: " + fileName, e);
                }
                PLUGIN_LOG.info("Plugin config: {} loaded.", fileName);
            } else {
                config = clazz.getDeclaredConstructor().newInstance();
                PLUGIN_LOG.info("Plugin config: {} not found.", fileName);
            }
            return config;
        } catch (final Throwable e) {
            PLUGIN_LOG.error("Unable to load plugin config: {}", fileName, e);
            PLUGIN_LOG.error("Config must be manually fixed or deleted");
            throw e;
        }
    }
}
